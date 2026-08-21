package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Domain event recorded when tokens are revoked.
 * Covers: rotation, logout, admin revoke, and bulk revoke operations.
 *
 * For BULK_REVOKE: revokedCount reflects total tokens revoked; revokedTokenHash is null.
 * For ROTATION/LOGOUT: revokedTokenHash identifies the specific revoked token.
 */
data class TokenRevokedEvent(
    val userId: Long,
    val revocationType: RevocationType,
    val revokedTokenHash: String? = null,
    val revokedAccessTokenJti: String? = null,
    val revokedCount: Int = 1,
    val reason: String? = null,
    val revokedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.revoked"
}
