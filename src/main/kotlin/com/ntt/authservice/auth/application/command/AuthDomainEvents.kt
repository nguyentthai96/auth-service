package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.DomainEvent

/**
 * Domain events published by command handlers.
 *
 * Note: UserRegisteredEvent REMOVED — consolidated into
 * com.ntt.authservice.auth.domain.event.UserRegisteredEvent (FR-002).
 *
 * Note: UserLoggedInEvent REMOVED — consolidated into
 * com.ntt.authservice.auth.domain.event.UserLoggedInEvent (user-login-event feature).
 * Enriched version with 13 fields replaces the minimal 2-field version.
 */

data class SessionRevokedEvent(
    val userId: Long,
    val revokedCount: Int
) : DomainEvent {
    override val eventType: String = "SESSIONS_REVOKED"
}
