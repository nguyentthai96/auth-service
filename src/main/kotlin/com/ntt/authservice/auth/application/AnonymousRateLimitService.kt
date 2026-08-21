package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.config.SecurityProperties.MfaProperties.LimitConfig
import com.ntt.authservice.shared.exception.AnonymousRateLimitedException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * IP-based rate limiting for anonymous token creation.
 * Supports two modes:
 * - Sliding window (Lua script, default) — weighted counter prevents burst-at-boundary (FR-002)
 * - Fixed window (INCR+EXPIRE, fallback) — consistent with LoginRateLimitService pattern
 *
 * Mode controlled by `app.security.anonymous.slidingWindowEnabled` (DD-105).
 * Fail-open strategy: allows request through when Redis is unavailable.
 * Lua script failure automatically falls back to fixed window (FR-011).
 */
@Service
class AnonymousRateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry,
    private val slidingWindowRateLimitScript: DefaultRedisScript<Long>
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
        try {
            if (securityProperties.anonymous.slidingWindowEnabled) {
                checkSlidingWindow(ipAddress, config)
            } else {
                checkFixedWindow(ipAddress, config)
            }
        } catch (ex: AnonymousRateLimitedException) {
            throw ex
        } catch (ex: RedisConnectionFailureException) {
            log.error("Redis unavailable for anonymous rate limit check — fail-open, ip={}", ipAddress, ex)
        } catch (ex: Exception) {
            log.error("Unexpected error in anonymous rate limit check — fail-open, ip={}", ipAddress, ex)
        }
    }

    /**
     * Sliding window rate limiter using Lua script (FR-002).
     * Weighted count: prev × (1 - elapsed/window) + current.
     * Falls back to fixed window on Lua execution failure (FR-011).
     */
    private fun checkSlidingWindow(ipAddress: String, config: LimitConfig) {
        val windowMs = config.windowSeconds * 1000
        val now = System.currentTimeMillis()
        val currentWindowId = now / windowMs
        val prevWindowId = currentWindowId - 1
        val elapsedSeconds = (now % windowMs) / 1000

        val currentKey = "$RATE_PREFIX$ipAddress:$currentWindowId"
        val prevKey = "$RATE_PREFIX$ipAddress:$prevWindowId"

        try {
            val result = redisTemplate.execute(
                slidingWindowRateLimitScript,
                listOf(currentKey, prevKey),
                config.maxAttempts.toString(),
                config.windowSeconds.toString(),
                elapsedSeconds.toString()
            )

            if (result == -1L) {
                log.warn(
                    "ANONYMOUS_RATE_LIMIT_EXCEEDED (sliding) ip={} maxAttempts={} window={}s",
                    ipAddress, config.maxAttempts, config.windowSeconds
                )
                meterRegistry.counter("auth.anonymous.rate_limited").increment()
                throw AnonymousRateLimitedException(retryAfterSeconds = config.windowSeconds)
            }
        } catch (ex: AnonymousRateLimitedException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Lua sliding window failed, falling back to fixed-window: {}", ex.message)
            checkFixedWindow(ipAddress, config)
        }
    }

    /**
     * Fixed-window rate limiter using INCR+EXPIRE (legacy/fallback).
     * Consistent with LoginRateLimitService pattern.
     */
    private fun checkFixedWindow(ipAddress: String, config: LimitConfig) {
        val key = "$RATE_PREFIX$ipAddress"
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
            meterRegistry.counter("auth.anonymous.rate_limited").increment()
            throw AnonymousRateLimitedException(retryAfterSeconds = ttl)
        }
    }
}
