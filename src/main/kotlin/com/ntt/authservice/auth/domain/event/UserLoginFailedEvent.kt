package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Domain event recorded when a user login attempt fails.
 * Captures failure context for audit trail, anomaly detection, and compliance.
 *
 * FR-002: Failure event with reason classification.
 * FR-009: Schema version 1 — backward-compatible via EventEnvelope wrapping.
 *
 * Follows TokenValidationFailedEvent pattern — failure event with reason enum.
 * Dot-notation convention for eventType: iam.user.login_failed
 *
 * Security: NEVER includes password — only username and contextual metadata.
 */
data class UserLoginFailedEvent(
    val usernameAttempted: String,
    val userId: Long?,
    val failureReason: LoginFailureReason,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceFingerprint: String?,
    val failedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.user.login_failed"
}
