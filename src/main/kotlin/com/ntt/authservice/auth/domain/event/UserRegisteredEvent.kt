package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent

/**
 * Enriched domain event published when a user is registered.
 * Consolidates duplicate definitions from EventPublisher.kt and AuthDomainEvents.kt (FR-002).
 *
 * FR-001: Enriched payload with full registration context
 * FR-002: Single source of truth — replaces both duplicates
 */
data class UserRegisteredEvent(
    val userId: Long,
    val username: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val domainCode: String,
    val domainId: Long?,
    val status: String,
    val registrationSource: String,
    val ipAddress: String?,
    val userAgent: String?
) : DomainEvent {
    override val eventType: String = "iam.user.registered"
}
