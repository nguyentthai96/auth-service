package com.ntt.authservice.auth.adapter.out.cache

import com.ntt.authservice.shared.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Redis adapter for account lockout state management (FR-010).
 *
 * Key patterns:
 * - lockout:failed:{userId}   — Failed attempt counter (INCR with TTL)
 * - lockout:templock:{userId} — Temporary lock count within 24h (for permanent lock threshold)
 * - lockout:unlock:{token}    — Self-service unlock token → userId mapping
 * - lockout:email-rate:{userId} — Rate limit for unlock email sending
 */
@Component
class RedisLockoutAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(RedisLockoutAdapter::class.java)

    companion object {
        private const val KEY_FAILED = "lockout:failed:"
        private const val KEY_TEMPLOCK = "lockout:templock:"
        private const val KEY_UNLOCK_TOKEN = "lockout:unlock:"
        private const val KEY_EMAIL_RATE = "lockout:email-rate:"
    }

    // --- Failed Attempt Counter ---

    /**
     * Increment failed login counter for user.
     * @return current failed count after increment
     */
    fun incrementFailedAttempts(userId: Long): Long {
        val key = "$KEY_FAILED$userId"
        val count = redisTemplate.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            val windowMinutes = securityProperties.lockout.failedAttemptWindowMinutes
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes.toLong()))
        }
        return count
    }

    /**
     * Get current failed attempt count.
     */
    fun getFailedAttempts(userId: Long): Long {
        val key = "$KEY_FAILED$userId"
        return redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0L
    }

    /**
     * Reset failed attempt counter (on successful login or unlock).
     */
    fun resetFailedAttempts(userId: Long) {
        redisTemplate.delete("$KEY_FAILED$userId")
    }

    // --- Temporary Lock Counter (for permanent lock escalation) ---

    /**
     * Increment temporary lock count within 24h.
     * @return current temp lock count
     */
    fun incrementTempLockCount(userId: Long): Long {
        val key = "$KEY_TEMPLOCK$userId"
        val count = redisTemplate.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(24))
        }
        return count
    }

    /**
     * Get current temporary lock count.
     */
    fun getTempLockCount(userId: Long): Long {
        val key = "$KEY_TEMPLOCK$userId"
        return redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0L
    }

    /**
     * Reset temporary lock counter.
     */
    fun resetTempLockCount(userId: Long) {
        redisTemplate.delete("$KEY_TEMPLOCK$userId")
    }

    // --- Self-service Unlock Token ---

    /**
     * Generate and store a self-service unlock token.
     * @return the generated token
     */
    fun createUnlockToken(userId: Long): String {
        val token = UUID.randomUUID().toString()
        val key = "$KEY_UNLOCK_TOKEN$token"
        val ttlMinutes = securityProperties.lockout.unlockTokenTtlMinutes
        redisTemplate.opsForValue().set(key, userId.toString(), Duration.ofMinutes(ttlMinutes.toLong()))
        return token
    }

    /**
     * Consume an unlock token — returns userId if valid, null if expired/used.
     * Token is deleted after consumption (one-time use).
     */
    fun consumeUnlockToken(token: String): Long? {
        val key = "$KEY_UNLOCK_TOKEN$token"
        val userId = redisTemplate.opsForValue().getAndDelete(key)
        return userId?.toLongOrNull()
    }

    // --- Email Rate Limiting ---

    /**
     * Check if unlock email can be sent (rate limit).
     * @return true if email can be sent
     */
    fun canSendUnlockEmail(userId: Long): Boolean {
        val key = "$KEY_EMAIL_RATE$userId"
        return redisTemplate.opsForValue().get(key) == null
    }

    /**
     * Mark unlock email as sent (set rate limit).
     */
    fun markUnlockEmailSent(userId: Long) {
        val key = "$KEY_EMAIL_RATE$userId"
        val rateLimitMinutes = securityProperties.lockout.unlockEmailRateLimitMinutes
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(rateLimitMinutes.toLong()))
    }
}
