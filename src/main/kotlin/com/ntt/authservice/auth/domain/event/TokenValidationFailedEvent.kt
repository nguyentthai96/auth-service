package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Domain event recorded when JWT validation fails for suspicious reasons.
 * Used for security monitoring and audit trail — NOT recorded for routine expiration.
 *
 * FR-012: Validation failure event recording.
 * Follows TokenIssuedEvent / TokenRevokedEvent pattern.
 *
 * Security: NEVER includes the raw token string — only JTI (UUID) for correlation.
 */
data class TokenValidationFailedEvent(
    val reason: ValidationFailureReason,
    val tokenJti: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val validatorName: String?,
    val failedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.validation-failed"
}
