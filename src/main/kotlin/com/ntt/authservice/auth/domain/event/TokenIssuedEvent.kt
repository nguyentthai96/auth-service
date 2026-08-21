package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Domain event recorded when JWT tokens are issued.
 * Captures full issuance context for audit trail, security monitoring, and compliance.
 *
 * Unified event type with IssuanceContext discriminator — covers LOGIN, REGISTRATION,
 * TOKEN_REFRESH, MFA_COMPLETION, and SSO token issuances.
 *
 * Security: stores refreshTokenHash (SHA-256) NOT raw token; accessTokenJti (UUID) NOT full JWT.
 */
data class TokenIssuedEvent(
    val userId: Long,
    val username: String,
    val domainCode: String,
    val domainId: Long,
    val issuanceContext: IssuanceContext,
    val accessTokenJti: String,
    val refreshTokenHash: String,
    val roles: List<String>,
    val permissions: List<String>,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val previousRefreshTokenHash: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val issuedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.issued"
}
