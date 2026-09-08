package com.ntt.authservice.shared.audit

import com.ntt.authservice.auth.application.cipher.EncryptedAuditService
import com.ntt.authservice.auth.application.port.out.AuditEvent
import com.ntt.authservice.auth.application.port.out.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant

/**
 * Audit log service for security-sensitive events (FR-015).
 * Persists to audit_logs table (immutable) AND publishes to event stream.
 * Includes sensitive data masking for PII fields.
 */
@Service
class AuditLogService(
    private val encryptedAuditService: ObjectProvider<EncryptedAuditService>,
    private val eventPublisher: ObjectProvider<EventPublisher>,
    private val auditLogRepository: ObjectProvider<AuditLogRepository>
) {

    private val log = LoggerFactory.getLogger("AUDIT")

    companion object {
        /** Fields that should be masked in audit log details. */
        private val SENSITIVE_PATTERNS = listOf(
            "password", "secret", "token", "credential", "ssn",
            "credit_card", "card_number", "cvv", "pin"
        )
        private const val MASKED_VALUE = "***MASKED***"
    }

    /**
     * Log an audit event with contextual information.
     * Persists to DB (immutable audit_logs table) AND publishes to event stream (FR-015).
     */
    fun logEvent(
        userId: Long?,
        action: AuditAction,
        entityType: String? = null,
        entityId: String? = null,
        details: String? = null
    ) {
        val request = getCurrentRequest()
        val ipAddress = request?.let { getClientIp(it) } ?: "unknown"
        val userAgent = request?.getHeader("User-Agent") ?: "unknown"
        val maskedDetails = maskSensitiveData(details)

        // Structured log output
        log.info(
            "AUDIT action={} userId={} entityType={} entityId={} ip={} userAgent={} details={}",
            action.name,
            userId,
            entityType ?: "-",
            entityId ?: "-",
            ipAddress,
            userAgent,
            maskedDetails ?: "-"
        )

        // Persist to audit_logs table (FR-015 — replaces TODO)
        auditLogRepository.ifAvailable?.let { repo ->
            try {
                val entity = AuditLogEntity().apply {
                    this.userId = userId
                    this.action = action.name
                    this.entityType = entityType
                    this.entityId = entityId
                    this.details = maskedDetails
                    this.ipAddress = ipAddress
                    this.userAgent = userAgent
                    this.eventTimestamp = Instant.now()
                }
                repo.save(entity)
            } catch (e: Exception) {
                log.error("Failed to persist audit log: action={} userId={} error={}", action.name, userId, e.message)
                // Non-blocking: audit persistence failure should not break the main flow
            }
        }

        // Publish audit event via EventPublisher (for cross-service audit consumption)
        eventPublisher.ifAvailable?.publish(
            AuditEvent(
                userId = userId,
                action = action.name,
                entityType = entityType,
                entityId = entityId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = maskedDetails
            )
        )
    }

    /**
     * Log an encrypted audit event — delegates to EncryptedAuditService.
     * Backward compatible: if EncryptedAuditService not available, falls back to logEvent.
     */
    fun logEncryptedAction(
        action: AuditAction,
        userId: String?,
        sensitivePayload: String? = null,
        keyIdUsed: String? = null
    ) {
        val service = encryptedAuditService.ifAvailable
        if (service != null) {
            service.logOperation(userId, action.name, sensitivePayload, keyIdUsed)
        } else {
            // Fallback to standard logging (without sensitive payload)
            log.info("AUDIT (unencrypted fallback) action={} userId={}", action.name, userId)
        }
    }

    /**
     * Mask sensitive data in audit log details (FR-015).
     * Replaces values of sensitive keys with MASKED_VALUE.
     */
    private fun maskSensitiveData(details: String?): String? {
        if (details.isNullOrBlank()) return details

        var masked = details
        SENSITIVE_PATTERNS.forEach { pattern ->
            // Mask key=value patterns (e.g., password=secret123 → password=***MASKED***)
            masked = masked!!.replace(
                Regex("(?i)($pattern)\\s*=\\s*[^,\\s]+"),
                "$1=$MASKED_VALUE"
            )
            // Mask JSON patterns (e.g., "password": "secret" → "password": "***MASKED***")
            masked = masked!!.replace(
                Regex("(?i)\"$pattern\"\\s*:\\s*\"[^\"]*\""),
                "\"$pattern\":\"$MASKED_VALUE\""
            )
        }
        return masked
    }

    private fun getCurrentRequest(): HttpServletRequest? {
        return try {
            val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            attrs?.request
        } catch (e: Exception) {
            null
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }
}

/**
 * Audit action types for auth-core-features.
 */
enum class AuditAction {
    MFA_SETUP,
    MFA_VERIFY_SUCCESS,
    MFA_VERIFY_FAILED,
    SSO_LOGIN,
    SSO_LINK,
    SSO_UNLINK,
    PASSWORD_CHANGED,
    FORCE_LOGOUT,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    MFA_OTP_LOCKED,
    MFA_LOGIN_LOCKED,
    MFA_ADMIN_UNLOCKED,
    TRUSTED_DEVICE_SET,
    SESSION_REVOKED,

    // E2EE Audit Actions
    E2EE_KEY_EXCHANGE,
    E2EE_KEY_ROTATED,
    E2EE_DECRYPT_REQUEST,
    E2EE_VAULT_ACCESS_GRANTED,
    E2EE_VAULT_ACCESS_DENIED,
    E2EE_REPLAY_BLOCKED,

    // FR-009: Account Lifecycle
    ACCOUNT_DEACTIVATED,
    ACCOUNT_REACTIVATED,
    DELETION_REQUESTED,
    DELETION_CANCELLED,
    DELETION_COMPLETED,
    DATA_EXPORT_REQUESTED,

    // FR-015: Enhanced Audit
    CONFIG_CHANGED,
    PERMISSION_CHANGED,
    SERVICE_TOKEN_ISSUED,
    SERVICE_TOKEN_VALIDATED,

    // Password algorithm migration audit
    PASSWORD_REHASHED
}
