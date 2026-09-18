package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.cache.RedisLockoutAdapter
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.event.AccountLockedEvent
import com.ntt.authservice.auth.domain.event.AccountUnlockedEvent
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AccountLockedException
import com.ntt.authservice.shared.exception.AccountLockedPermanentException
import com.ntt.notification.client.NotificationPort
import com.ntt.notification.client.NotificationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Central orchestrator for account lockout policy (FR-010).
 *
 * Responsibilities:
 * - Record failed login attempts (Redis counter)
 * - Trigger temporary lock → permanent lock escalation
 * - Send lock notification email with self-service unlock link
 * - Validate lockout status before login
 * - Process self-service unlock and admin unlock
 */
@Service
class AccountLockoutService(
    private val redisLockoutAdapter: RedisLockoutAdapter,
    private val userPort: UserPort,
    private val securityProperties: SecurityProperties,
    private val auditLogService: AuditLogService,
    private val notificationPort: ObjectProvider<NotificationPort>
) {

    private val log = LoggerFactory.getLogger(AccountLockoutService::class.java)
    private val lockoutConfig get() = securityProperties.lockout

    /**
     * Check if user account is locked. Auto-unlock if TTL expired.
     * @throws AccountLockedException if temporary lock is still active
     * @throws AccountLockedPermanentException if permanently locked
     */
    fun validateLockoutStatus(user: User) {
        when (val status = user.status) {
            is UserStatus.Locked -> {
                if (user.isLockExpired()) {
                    user.unlockIfExpired()
                    redisLockoutAdapter.resetFailedAttempts(user.id.value)
                    userPort.save(user)
                    log.info("AUTO_UNLOCK userId={} (TTL expired)", user.id.value)
                } else {
                    throw AccountLockedException(status.until, status.reason)
                }
            }
            is UserStatus.LockedPermanent -> {
                throw AccountLockedPermanentException(status.reason)
            }
            else -> { /* not locked — continue */ }
        }
    }

    /**
     * Record a failed login attempt and apply lockout policy.
     * Called AFTER password verification fails.
     *
     * Flow:
     * 1. Increment Redis failed counter
     * 2. If >= maxFailedAttempts → lock user
     * 3. Increment temp lock counter
     * 4. If temp lock count >= persistentLockThreshold → permanent lock
     * 5. Send notification email
     */
    fun recordFailedAttempt(user: User, clientIp: String?) {
        if (!lockoutConfig.enabled) return

        val failedCount = redisLockoutAdapter.incrementFailedAttempts(user.id.value)
        log.debug("LOCKOUT_FAILED_ATTEMPT userId={} count={}/{}", user.id.value, failedCount, lockoutConfig.maxFailedAttempts)

        if (failedCount >= lockoutConfig.maxFailedAttempts) {
            applyLock(user, clientIp)
        }
    }

    /**
     * Get current failed attempt count for CAPTCHA threshold check.
     */
    fun getFailedAttemptCount(userId: Long): Long {
        return redisLockoutAdapter.getFailedAttempts(userId)
    }

    /**
     * Reset lockout state on successful login.
     */
    fun resetOnSuccess(userId: Long) {
        redisLockoutAdapter.resetFailedAttempts(userId)
    }

    /**
     * Process self-service unlock (FR-013).
     * @return userId if token was valid and account unlocked, null otherwise
     */
    fun processSelfServiceUnlock(token: String): Long? {
        val userId = redisLockoutAdapter.consumeUnlockToken(token) ?: return null

        val user = userPort.findById(userId) ?: return null

        // Self-service unlock only for temporary locks
        if (user.status !is UserStatus.Locked) {
            log.warn("SELF_UNLOCK_REJECTED userId={} status={}", userId, UserStatus.toDbString(user.status))
            return null
        }

        user.unlockBySelfService()
        userPort.save(user)
        redisLockoutAdapter.resetFailedAttempts(userId)

        auditLogService.logEvent(
            userId = userId,
            action = AuditAction.ACCOUNT_UNLOCKED_SELF_SERVICE,
            entityType = "User",
            entityId = userId.toString(),
            details = "Self-service unlock via email link"
        )

        log.info("SELF_UNLOCK_SUCCESS userId={}", userId)
        return userId
    }

    /**
     * Admin unlock (FR-009).
     */
    fun processAdminUnlock(userId: Long, adminId: Long) {
        val user = userPort.findById(userId) ?: return

        user.unlockByAdmin()
        userPort.save(user)
        redisLockoutAdapter.resetFailedAttempts(userId)
        redisLockoutAdapter.resetTempLockCount(userId)

        auditLogService.logEvent(
            userId = userId,
            action = AuditAction.ACCOUNT_UNLOCKED_ADMIN,
            entityType = "User",
            entityId = userId.toString(),
            details = """{"unlockedBy": $adminId}"""
        )

        log.info("ADMIN_UNLOCK userId={} by adminId={}", userId, adminId)
    }

    // --- Private ---

    private fun applyLock(user: User, clientIp: String?) {
        val tempLockCount = redisLockoutAdapter.incrementTempLockCount(user.id.value)

        if (tempLockCount >= lockoutConfig.persistentLockThreshold) {
            // Permanent lock
            user.lockPermanently()
            userPort.save(user)
            redisLockoutAdapter.resetFailedAttempts(user.id.value)

            auditLogService.logEvent(
                userId = user.id.value,
                action = AuditAction.ACCOUNT_LOCKED_PERMANENT,
                entityType = "User",
                entityId = user.id.value.toString(),
                details = """{"tempLockCount": $tempLockCount, "clientIp": "$clientIp"}"""
            )

            sendPermanentLockEmail(user, clientIp)
            log.warn("PERMANENT_LOCK userId={} tempLockCount={}", user.id.value, tempLockCount)
        } else {
            // Temporary lock
            val lockDuration = lockoutConfig.lockDurationMinutes * 60L
            user.recordFailedLogin(1, lockDuration) // force lock
            userPort.save(user)
            redisLockoutAdapter.resetFailedAttempts(user.id.value)

            auditLogService.logEvent(
                userId = user.id.value,
                action = AuditAction.ACCOUNT_LOCKED_TEMPORARY,
                entityType = "User",
                entityId = user.id.value.toString(),
                details = """{"duration": ${lockoutConfig.lockDurationMinutes}, "tempLockCount": $tempLockCount, "clientIp": "$clientIp"}"""
            )

            // Generate unlock token and send email
            if (lockoutConfig.selfServiceUnlockEnabled) {
                sendTempLockEmail(user, clientIp)
            }

            log.warn("TEMP_LOCK userId={} duration={}min tempLockCount={}", user.id.value, lockoutConfig.lockDurationMinutes, tempLockCount)
        }
    }

    private fun sendTempLockEmail(user: User, clientIp: String?) {
        if (!redisLockoutAdapter.canSendUnlockEmail(user.id.value)) {
            log.debug("UNLOCK_EMAIL_RATE_LIMITED userId={}", user.id.value)
            return
        }

        val unlockToken = redisLockoutAdapter.createUnlockToken(user.id.value)
        redisLockoutAdapter.markUnlockEmailSent(user.id.value)

        val templateData = mapOf(
            "userName" to user.fullName,
            "lockDuration" to lockoutConfig.lockDurationMinutes.toString(),
            "unlockToken" to unlockToken,
            "clientIp" to (clientIp ?: "Unknown"),
            "lockTime" to Instant.now().toString()
        )

        try {
            notificationPort.ifAvailable?.enqueue(
                NotificationRequest(
                    recipient = user.email.value,
                    templateCode = "ACCOUNT_LOCKED_TEMPORARY",
                    templateData = templateData,
                    channel = "EMAIL",
                    priority = "HIGH",
                    sourceService = "auth-service",
                    createdBy = user.id.value
                )
            )
        } catch (e: Exception) {
            log.error("Failed to send temp lock email: userId={} error={}", user.id.value, e.message)
        }
    }

    private fun sendPermanentLockEmail(user: User, clientIp: String?) {
        val templateData = mapOf(
            "userName" to user.fullName,
            "clientIp" to (clientIp ?: "Unknown"),
            "lockTime" to Instant.now().toString(),
            "adminContactInfo" to "Please contact your system administrator to unlock your account."
        )

        try {
            notificationPort.ifAvailable?.enqueue(
                NotificationRequest(
                    recipient = user.email.value,
                    templateCode = "ACCOUNT_LOCKED_PERMANENT",
                    templateData = templateData,
                    channel = "EMAIL",
                    priority = "URGENT",
                    sourceService = "auth-service",
                    createdBy = user.id.value
                )
            )
        } catch (e: Exception) {
            log.error("Failed to send permanent lock email: userId={} error={}", user.id.value, e.message)
        }
    }
}
