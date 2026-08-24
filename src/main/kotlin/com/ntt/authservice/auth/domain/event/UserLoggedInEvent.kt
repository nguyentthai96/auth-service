package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Enriched domain event published when a user successfully logs in.
 * Captures full authentication context: identity, device, MFA status, session info.
 *
 * FR-001: Enriched payload with full login context.
 * FR-009: Schema version 1 — backward-compatible via EventEnvelope wrapping.
 * FR-011: Complementary with TokenIssuedEvent — captures authentication context (who, where, how),
 *         while TokenIssuedEvent captures token context (JTI, roles, permissions, expiry).
 *
 * Follows UserRegisteredEvent pattern — same package, same DomainEvent interface, data class with eventType override.
 * Dot-notation convention for eventType: iam.user.logged_in
 */
data class UserLoggedInEvent(
    val userId: Long,
    val username: String,
    val domainCode: String,
    val domainId: Long?,
    val loginMethod: String = "PASSWORD",
    val mfaBypassed: Boolean,
    val mfaMethod: String?,
    val isNewDevice: Boolean,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceFingerprint: String?,
    val sessionPromotionStatus: String?,
    val loggedInAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.user.logged_in"
}
