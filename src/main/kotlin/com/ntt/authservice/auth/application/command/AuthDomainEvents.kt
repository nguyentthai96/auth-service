package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.DomainEvent

/**
 * Domain events published by command handlers.
 *
 * Note: UserRegisteredEvent REMOVED — consolidated into
 * com.ntt.authservice.auth.domain.event.UserRegisteredEvent (FR-002).
 */

data class UserLoggedInEvent(
    val userId: Long,
    val domainCode: String
) : DomainEvent {
    override val eventType: String = "USER_LOGGED_IN"
}

data class SessionRevokedEvent(
    val userId: Long,
    val revokedCount: Int
) : DomainEvent {
    override val eventType: String = "SESSIONS_REVOKED"
}
