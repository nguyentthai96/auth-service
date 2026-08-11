package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Multi-dimensional login rate limiting service.
 * Checks IP, username, and device fingerprint in parallel.
 *
 * Uses same Redis INCR + EXPIRE pattern as MfaRateLimitService.
 * Fail-open strategy: allows request through when Redis is unavailable.
 */
@Service
class LoginRateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(LoginRateLimitService::class.java)

    companion object {
        private const val ATTEMPTS_PREFIX = "rate:login:attempts:"
        private const val LOCK_PREFIX = "rate:login:lock:"
        private const val LOCK_VALUE = "locked"
    }

    /**
     * Check all dimensions (IP, username, device) for rate limit violations.
     * Throws RateLimitExceededException on first dimension that exceeds threshold.
     *
     * @param ip client IP address
     * @param username login username
     * @param deviceFingerprint device fingerprint hash (nullable)
     */
    fun checkMultiDimensional(ip: String, username: String, deviceFingerprint: String?) {
        checkDimension("ip", ip, securityProperties.loginRateLimit.ip)
        checkDimension("user", username, securityProperties.loginRateLimit.username)
        if (!deviceFingerprint.isNullOrBlank()) {
            checkDimension("device", deviceFingerprint, securityProperties.loginRateLimit.device)
        }
    }

    /**
     * Record a failed login attempt for all dimensions.
     */
    fun recordFailedAttempt(ip: String, username: String, deviceFingerprint: String?) {
        incrementDimension("ip", ip, securityProperties.loginRateLimit.ip)
        incrementDimension("user", username, securityProperties.loginRateLimit.username)
        if (!deviceFingerprint.isNullOrBlank()) {
            incrementDimension("device", deviceFingerprint, securityProperties.loginRateLimit.device)
        }
    }

    /**
     * Reset counters on successful login.
     */
    fun resetOnSuccess(ip: String, username: String, deviceFingerprint: String?) {
        try {
            val keysToDelete = mutableListOf(
                attemptsKey("ip", ip),
                attemptsKey("user", username)
            )
            if (!deviceFingerprint.isNullOrBlank()) {
                keysToDelete.add(attemptsKey("device", deviceFingerprint))
            }
            redisTemplate.delete(keysToDelete)
        } catch (ex: Exception) {
            log.error("Failed to reset login rate limit counters — non-critical", ex)
        }
    }

    private fun checkDimension(dimension: String, key: String, config: SecurityProperties.MfaProperties.LimitConfig) {
        try {
            val lockKey = lockKey(dimension, key)

            // Check existing lock
            if (redisTemplate.hasKey(lockKey) == true) {
                val ttl = redisTemplate.getExpire(lockKey) ?: config.lockSeconds
                throw RateLimitExceededException(
                    retryAfterSeconds = ttl,
                    dimension = dimension.uppercase()
                )
            }
        } catch (ex: RateLimitExceededException) {
            throw ex
        } catch (ex: RedisConnectionFailureException) {
            log.error("Redis unavailable for login rate limit check — fail-open, dimension={}, key={}", dimension, key, ex)
        } catch (ex: Exception) {
            log.error("Unexpected error in login rate limit check — fail-open, dimension={}", dimension, ex)
        }
    }

    private fun incrementDimension(dimension: String, key: String, config: SecurityProperties.MfaProperties.LimitConfig) {
        try {
            val ops = redisTemplate.opsForValue()
            val attemptsKey = attemptsKey(dimension, key)
            val lockKey = lockKey(dimension, key)

            // Atomic increment
            val attempts = ops.increment(attemptsKey) ?: 1

            // Set TTL on first increment (window start)
            if (attempts == 1L) {
                redisTemplate.expire(attemptsKey, Duration.ofSeconds(config.windowSeconds))
            }

            // Check threshold — create lock if exceeded
            if (attempts > config.maxAttempts) {
                ops.set(lockKey, LOCK_VALUE, Duration.ofSeconds(config.lockSeconds))
                log.warn(
                    "LOGIN_RATE_LIMIT_LOCKED dimension={} key={} attempts={} lockDuration={}s",
                    dimension, key, attempts, config.lockSeconds
                )
            }
        } catch (ex: RedisConnectionFailureException) {
            log.error("Redis unavailable for login rate limit increment — fail-open, dimension={}", dimension, ex)
        } catch (ex: Exception) {
            log.error("Unexpected error in login rate limit increment — fail-open, dimension={}", dimension, ex)
        }
    }

    private fun attemptsKey(dimension: String, key: String): String =
        "$ATTEMPTS_PREFIX$dimension:$key"

    private fun lockKey(dimension: String, key: String): String =
        "$LOCK_PREFIX$dimension:$key"
}
