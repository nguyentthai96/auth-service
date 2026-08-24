package com.ntt.authservice.auth.application

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Cache
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Two-tier (Caffeine L1 + Redis L2) token blacklist cache with DB fallback.
 * Provides sub-millisecond blacklist lookups on the hot path (JwtAuthFilter).
 *
 * Architecture: L1 Caffeine (in-process) → L2 Redis (distributed) → DB (source of truth).
 * Circuit breaker: skips Redis after consecutive failures to prevent cascading latency.
 *
 * FR-001: Three-level blacklist lookup.
 * FR-015: Redis resilience with circuit breaker.
 * FR-016: Write-through on token revocation.
 * OBS-001: Micrometer metrics for cache tier hit/miss, circuit breaker, Caffeine size.
 */
@Service
class TokenBlacklistCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(TokenBlacklistCacheService::class.java)

    private val props = securityProperties.blacklist

    /** L1 in-process cache — programmatic Caffeine (NOT Spring Cache). */
    private val caffeineCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(props.caffeineTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(props.caffeineMaxSize)
        .build()

    // Circuit breaker state for Redis operations
    private val consecutiveFailures = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)

    init {
        // OBS-001: Gauge for L1 Caffeine cache size
        Gauge.builder("auth.token.blacklist.caffeine.size") { caffeineCache.estimatedSize().toDouble() }
            .description("Current number of entries in L1 Caffeine blacklist cache")
            .register(meterRegistry)
    }

    /**
     * Check if a token JTI is blacklisted.
     * Lookup order: L1 Caffeine → L2 Redis → DB fallback.
     * Populates upper tiers on cache miss.
     */
    fun isBlacklisted(jti: String): Boolean {
        // L1: Caffeine (in-process, ~nanoseconds)
        caffeineCache.getIfPresent(jti)?.let {
            meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l1_caffeine", "result", "hit").increment()
            return it
        }
        meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l1_caffeine", "result", "miss").increment()

        // L2: Redis (distributed, ~milliseconds)
        if (!isCircuitBreakerOpen()) {
            try {
                val redisKey = "${props.redisKeyPrefix}$jti"
                val exists = redisTemplate.hasKey(redisKey)
                if (exists) {
                    consecutiveFailures.set(0)
                    caffeineCache.put(jti, true)
                    meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l2_redis", "result", "hit").increment()
                    return true
                }
                consecutiveFailures.set(0)
                meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l2_redis", "result", "miss").increment()
            } catch (e: Exception) {
                recordRedisFailure(e, "isBlacklisted")
            }
        }

        // L3: DB fallback (source of truth)
        val existsInDb = tokenBlacklistRepository.existsByTokenJti(jti)
        if (existsInDb) {
            meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l3_db", "result", "hit").increment()
            caffeineCache.put(jti, true)
            // Back-fill Redis on DB hit (best effort)
            tryRedisSet(jti, props.caffeineTtlSeconds)
        } else {
            meterRegistry.counter("auth.token.blacklist.lookup", "tier", "l3_db", "result", "miss").increment()
        }

        return existsInDb
    }

    /**
     * Add a token JTI to the blacklist cache (write-through).
     * Called from TokenStorePersistenceAdapter after DB persist.
     *
     * @param jti Token JTI to blacklist.
     * @param remainingSeconds Remaining token lifetime (Redis TTL).
     */
    fun addToBlacklist(jti: String, remainingSeconds: Long) {
        // L1: Caffeine (always succeeds)
        caffeineCache.put(jti, true)

        // L2: Redis with 1 retry
        if (!tryRedisSet(jti, remainingSeconds)) {
            // Retry once after 100ms delay
            try {
                Thread.sleep(100)
                tryRedisSet(jti, remainingSeconds)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Attempt Redis SET with TTL. Returns true on success.
     */
    private fun tryRedisSet(jti: String, ttlSeconds: Long): Boolean {
        if (isCircuitBreakerOpen()) return false

        return try {
            val redisKey = "${props.redisKeyPrefix}$jti"
            val effectiveTtl = if (ttlSeconds > 0) ttlSeconds else props.caffeineTtlSeconds
            redisTemplate.opsForValue().set(redisKey, "1", Duration.ofSeconds(effectiveTtl))
            consecutiveFailures.set(0)
            true
        } catch (e: Exception) {
            recordRedisFailure(e, "addToBlacklist")
            false
        }
    }

    /**
     * Circuit breaker: check if Redis should be skipped.
     */
    private fun isCircuitBreakerOpen(): Boolean {
        val failures = consecutiveFailures.get()
        if (failures < props.circuitBreakerThreshold) return false

        val elapsed = System.currentTimeMillis() - lastFailureTime.get()
        if (elapsed > props.circuitBreakerResetSeconds * 1000) {
            // Reset circuit breaker — allow retry
            consecutiveFailures.set(0)
            meterRegistry.counter("auth.token.blacklist.circuit_breaker", "transition", "reset").increment()
            return false
        }

        return true
    }

    /**
     * Record a Redis failure for circuit breaker tracking.
     */
    private fun recordRedisFailure(e: Exception, operation: String) {
        val count = consecutiveFailures.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())
        if (count == props.circuitBreakerThreshold) {
            log.warn(
                "Redis circuit breaker OPEN after {} consecutive failures (operation={}). " +
                    "Skipping Redis for {}s. Last error: {}",
                count, operation, props.circuitBreakerResetSeconds, e.message
            )
            meterRegistry.counter("auth.token.blacklist.circuit_breaker", "transition", "opened").increment()
        } else {
            log.debug("Redis failure in {}: {} (consecutive={})", operation, e.message, count)
        }
    }
}
