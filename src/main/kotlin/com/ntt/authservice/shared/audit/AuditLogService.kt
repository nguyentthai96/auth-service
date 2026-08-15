package com.ntt.authservice.shared.audit

import com.ntt.authservice.auth.application.cipher.EncryptedAuditService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Audit log service for security-sensitive events.
 * Logs to structured logger (JSON) and optionally to audit_log table.
 */
@Service
class AuditLogService(
    private val encryptedAuditService: ObjectProvider<EncryptedAuditService>
) {

    private val log = LoggerFactory.getLogger("AUDIT")

    /**
     * Log an audit event with contextual information.
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

        log.info(
            "AUDIT action={} userId={} entityType={} entityId={} ip={} userAgent={} details={}",
            action.name,
            userId,
            entityType ?: "-",
            entityId ?: "-",
            ipAddress,
            userAgent,
            details ?: "-"
        )

        // TODO: persist to audit_log table for compliance requirements
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

    // E2EE Audit Actions
    E2EE_KEY_EXCHANGE,
    E2EE_KEY_ROTATED,
    E2EE_DECRYPT_REQUEST,
    E2EE_VAULT_ACCESS_GRANTED,
    E2EE_VAULT_ACCESS_DENIED,
    E2EE_REPLAY_BLOCKED
}
