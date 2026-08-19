package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousRateLimitedException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * IP-based rate limiting for anonymous token creation.
 * Uses Redis INCR + EXPIRE fixed window pattern (consistent with LoginRateLimitService).
 * Fail-open strategy: allows request through when Redis is unavailable.
 */
@Service
class AnonymousRateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(AnonymousRateLimitService::class.java)

    companion object {
        private const val RATE_PREFIX = "anon:rate:"
    }

    /**
     * Check and increment rate limit for anonymous token creation by IP.
     * Throws AnonymousRateLimitedException when the limit is exceeded.
     *
     * @param ipAddress client IP address
     */
    fun checkRateLimit(ipAddress: String) {
        val config = securityProperties.anonymous.rateLimit
        val key = "$RATE_PREFIX$ipAddress"

        try {
            val ops = redisTemplate.opsForValue()

            // Atomic increment
            val attempts = ops.increment(key) ?: 1

            // Set TTL on first increment (window start)
            if (attempts == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(config.windowSeconds))
            }

            // Check threshold
            if (attempts > config.maxAttempts) {
                val ttl = redisTemplate.getExpire(key) ?: config.windowSeconds
                log.warn(
                    "ANONYMOUS_RATE_LIMIT_EXCEEDED ip={} attempts={} maxAttempts={} retryAfter={}s",
                    ipAddress, attempts, config.maxAttempts, ttl
                )
                throw AnonymousRateLimitedException(retryAfterSeconds = ttl)
            }
        } catch (ex: AnonymousRateLimitedException) {
            throw ex
        } catch (ex: RedisConnectionFailureException) {
            log.error("Redis unavailable for anonymous rate limit check — fail-open, ip={}", ipAddress, ex)
        } catch (ex: Exception) {
            log.error("Unexpected error in anonymous rate limit check — fail-open, ip={}", ipAddress, ex)
        }
    }
}
