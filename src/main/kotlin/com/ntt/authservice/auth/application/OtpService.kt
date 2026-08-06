package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.MfaCodeInvalidException
import com.ntt.authservice.shared.exception.MfaMaxAttemptsException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration

/**
 * OTP generation, storage (Redis), and verification for SMS/Email MFA.
 */
@Service
class OtpService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(OtpService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private const val OTP_KEY_PREFIX = "otp:"
        private const val RATE_LIMIT_PREFIX = "otp:ratelimit:"
        private const val ATTEMPTS_SUFFIX = ":attempts"
        private const val OTP_LENGTH = 6
        private const val RATE_LIMIT_SECONDS = 60L
    }

    /**
     * Generate a 6-digit OTP and store in Redis with TTL.
     * @return the generated OTP code (caller sends via SMS/Email)
     */
    fun generateOtp(userId: Long, channel: String): String {
        // Rate limit: max 1 OTP per 60 seconds per channel
        val rateLimitKey = "$RATE_LIMIT_PREFIX$userId:$channel"
        if (redisTemplate.hasKey(rateLimitKey) == true) {
            throw MfaCodeInvalidException("Please wait before requesting a new code")
        }

        val code = String.format("%06d", secureRandom.nextInt(1_000_000))
        val ttl = Duration.ofSeconds(securityProperties.mfa.otpTtlSeconds)
        val key = otpKey(userId, channel)
        val attemptsKey = key + ATTEMPTS_SUFFIX

        val ops = redisTemplate.opsForValue()
        ops.set(key, code, ttl)
        ops.set(attemptsKey, "0", ttl)

        // Set rate limit key
        ops.set(rateLimitKey, "1", Duration.ofSeconds(RATE_LIMIT_SECONDS))

        log.debug("OTP generated for userId={}, channel={}", userId, channel)
        return code
    }

    /**
     * Verify OTP code. On success: deletes keys. On failure: increments attempts.
     * @throws MfaCodeInvalidException if code is wrong
     * @throws MfaMaxAttemptsException if max attempts exceeded
     */
    fun verifyOtp(userId: Long, channel: String, code: String): Boolean {
        val key = otpKey(userId, channel)
        val attemptsKey = key + ATTEMPTS_SUFFIX
        val ops = redisTemplate.opsForValue()

        val storedCode = ops.get(key)
            ?: throw MfaCodeInvalidException("OTP expired or not found")

        // Constant-time comparison
        if (!java.security.MessageDigest.isEqual(storedCode.toByteArray(), code.toByteArray())) {
            val attempts = ops.increment(attemptsKey) ?: 1
            if (attempts >= securityProperties.mfa.maxAttempts) {
                deleteOtp(userId, channel)
                throw MfaMaxAttemptsException()
            }
            throw MfaCodeInvalidException("Invalid OTP code")
        }

        // Success — clean up
        deleteOtp(userId, channel)
        log.debug("OTP verified for userId={}, channel={}", userId, channel)
        return true
    }

    /**
     * Delete OTP keys from Redis.
     */
    fun deleteOtp(userId: Long, channel: String) {
        val key = otpKey(userId, channel)
        redisTemplate.delete(listOf(key, key + ATTEMPTS_SUFFIX))
    }

    private fun otpKey(userId: Long, channel: String): String {
        return "${OTP_KEY_PREFIX}${userId}:${channel}"
    }
}
