package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.application.event.RateLimitExceededEvent
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.MfaAccountLockedException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * MFA rate limiting service — centralized logic for OTP verify and MFA login attempt tracking.
 *
 * Uses Redis INCR + EXPIRE for atomic, distributed counter management.
 * Fail-open strategy: allows request through when Redis is unavailable (FR-017).
 *
 * Reuses OtpService.verifyOtp() Redis pattern (INCR + conditional check).
 */
@Service
class MfaRateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties,
    private val auditLogService: AuditLogService,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(MfaRateLimitService::class.java)

    companion object {
        private const val ATTEMPTS_PREFIX = "mfa:verify:attempts:"
        private const val LOCK_PREFIX = "mfa:verify:lock:"
        private const val LOCK_VALUE = "locked"
    }

    /**
     * Check if user is locked, then increment attempt counter.
     * Throws MfaAccountLockedException if already locked or threshold exceeded.
     *
     * @param userId target user ID
     * @param type rate limit type (OTP_VERIFY or MFA_LOGIN)
     * @throws MfaAccountLockedException when locked or threshold exceeded
     */
    fun checkAndIncrement(userId: Long, type: RateLimitType) {
        val config = getConfig(type)
        val lockKey = lockKey(userId, type)
        val attemptsKey = attemptsKey(userId, type)

        try {
            val ops = redisTemplate.opsForValue()

            // Check existing lock
            if (redisTemplate.hasKey(lockKey) == true) {
                val ttl = redisTemplate.getExpire(lockKey) ?: config.lockSeconds
                throw MfaAccountLockedException(
                    retryAfterSeconds = ttl,
                    lockType = type.key
                )
            }

            // Atomic increment
            val attempts = ops.increment(attemptsKey) ?: 1

            // Set TTL on first increment (window start)
            if (attempts == 1L) {
                redisTemplate.expire(attemptsKey, Duration.ofSeconds(config.windowSeconds))
            }

            // Check threshold
            if (attempts > config.maxAttempts) {
                // Create lock with TTL
                ops.set(lockKey, LOCK_VALUE, Duration.ofSeconds(config.lockSeconds))

                // Audit log
                val auditAction = when (type) {
                    RateLimitType.OTP_VERIFY -> AuditAction.MFA_OTP_LOCKED
                    RateLimitType.MFA_LOGIN -> AuditAction.MFA_LOGIN_LOCKED
                }
                auditLogService.logEvent(
                    userId = userId,
                    action = auditAction,
                    entityType = "User",
                    entityId = userId.toString(),
                    details = "attempts=$attempts, lockDuration=${config.lockSeconds}s, type=${type.key}"
                )

                // Structured WARN log (FR-009)
                log.warn(
                    "MFA_RATE_LIMIT_LOCKED userId={} lockType={} attemptCount={} lockDurationSeconds={}",
                    userId, type.key, attempts, config.lockSeconds
                )

                // Publish event for alerting (FR-010)
                eventPublisher.publishEvent(
                    RateLimitExceededEvent(
                        userId = userId,
                        lockType = type.key,
                        attemptCount = attempts.toInt(),
                        lockDurationSeconds = config.lockSeconds
                    )
                )

                throw MfaAccountLockedException(
                    retryAfterSeconds = config.lockSeconds,
                    lockType = type.key
                )
            }
        } catch (ex: MfaAccountLockedException) {
            throw ex // re-throw our own exception
        } catch (ex: RedisConnectionFailureException) {
            // Fail-open: allow request through when Redis unavailable (FR-017)
            log.error("Redis unavailable for rate limiting — fail-open, userId={}", userId, ex)
        } catch (ex: Exception) {
            // Catch-all for unexpected Redis errors — still fail-open
            log.error("Unexpected error in rate limiting — fail-open, userId={}", userId, ex)
        }
    }

    /**
     * Check if user is currently locked for a given rate limit type.
     */
    fun isLocked(userId: Long, type: RateLimitType): Boolean {
        return try {
            redisTemplate.hasKey(lockKey(userId, type)) == true
        } catch (ex: Exception) {
            log.error("Redis error checking lock status — assuming unlocked, userId={}", userId, ex)
            false
        }
    }

    /**
     * Reset all rate limit counters for user (on successful verification).
     */
    fun resetCounters(userId: Long) {
        try {
            val keysToDelete = RateLimitType.entries.flatMap { type ->
                listOf(attemptsKey(userId, type), lockKey(userId, type))
            }
            redisTemplate.delete(keysToDelete)
            log.debug("Rate limit counters reset for userId={}", userId)
        } catch (ex: Exception) {
            log.error("Failed to reset rate limit counters — non-critical, userId={}", userId, ex)
        }
    }

    /**
     * Admin manual unlock — clears all lock and attempt keys for user.
     */
    fun adminUnlock(userId: Long) {
        try {
            val keysToDelete = RateLimitType.entries.flatMap { type ->
                listOf(attemptsKey(userId, type), lockKey(userId, type))
            }
            redisTemplate.delete(keysToDelete)

            auditLogService.logEvent(
                userId = userId,
                action = AuditAction.MFA_ADMIN_UNLOCKED,
                entityType = "User",
                entityId = userId.toString(),
                details = "Admin manual unlock — all MFA rate limit locks cleared"
            )
            log.info("Admin unlock completed for userId={}", userId)
        } catch (ex: Exception) {
            log.error("Failed to admin unlock userId={}", userId, ex)
            throw ex
        }
    }

    /**
     * Get lock info for all rate limit types.
     *
     * @return map of type → LockInfo (null if not locked)
     */
    fun getLockInfo(userId: Long): Map<RateLimitType, LockInfo?> {
        return try {
            RateLimitType.entries.associateWith { type ->
                val lockKey = lockKey(userId, type)
                if (redisTemplate.hasKey(lockKey) == true) {
                    val ttl = redisTemplate.getExpire(lockKey) ?: 0
                    LockInfo(
                        userId = userId,
                        lockType = type,
                        retryAfterSeconds = ttl,
                        lockedAt = Instant.now().minusSeconds(getConfig(type).lockSeconds - ttl)
                    )
                } else {
                    null
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to get lock info for userId={}", userId, ex)
            emptyMap()
        }
    }

    private fun getConfig(type: RateLimitType): SecurityProperties.MfaProperties.LimitConfig {
        val rateLimit = securityProperties.mfa.rateLimit
        return when (type) {
            RateLimitType.OTP_VERIFY -> rateLimit.otp
            RateLimitType.MFA_LOGIN -> rateLimit.login
        }
    }

    private fun attemptsKey(userId: Long, type: RateLimitType): String =
        "$ATTEMPTS_PREFIX$userId:${type.key}"

    private fun lockKey(userId: Long, type: RateLimitType): String =
        "$LOCK_PREFIX$userId:${type.key}"
}

/**
 * Rate limit types for MFA verification flows.
 */
enum class RateLimitType(val key: String) {
    OTP_VERIFY("OTP"),
    MFA_LOGIN("MFA_LOGIN")
}

/**
 * Lock information for admin/monitoring purposes.
 */
data class LockInfo(
    val userId: Long,
    val lockType: RateLimitType,
    val retryAfterSeconds: Long,
    val lockedAt: Instant
)
