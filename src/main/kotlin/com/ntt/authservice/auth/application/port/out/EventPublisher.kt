package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for publishing domain events.
 */
interface EventPublisher {
    fun publish(event: DomainEvent)
}

/**
 * Base marker for domain events.
 */
interface DomainEvent {
    val eventType: String
}

/**
 * Event published when a user is registered.
 */
data class UserRegisteredEvent(
    val userId: Long,
    val username: String,
    val domainCode: String
) : DomainEvent {
    override val eventType: String = "user.registered"
}

/**
 * Event published when permissions are changed (triggers cache invalidation).
 */
data class PermissionChangedEvent(
    val userId: Long? = null,
    val domainId: Long? = null,
    override val eventType: String = "iam.permission.changed"
) : DomainEvent

/**
 * Event published when a user is provisioned via SSO JIT flow (FR-007).
 * Used for downstream sync (e.g., Kafka topic: iam.user.sso_provisioned).
 */
data class SsoProvisionedEvent(
    val userId: Long,
    val provider: String,
    val email: String?,
    val domainCode: String
) : DomainEvent {
    override val eventType: String = "iam.user.sso_provisioned"
}

/**
 * Event published when an account is deactivated (FR-009).
 * Consumed by account-service to deactivate profile, devices, preferences.
 */
data class AccountDeactivatedEvent(
    val userId: Long,
    val reason: String? = null
) : DomainEvent {
    override val eventType: String = "iam.account.deactivated"
}

/**
 * Event published when an account is permanently deleted (FR-009 — GDPR).
 * Consumed by account-service to purge PII data.
 */
data class AccountDeletedEvent(
    val userId: Long
) : DomainEvent {
    override val eventType: String = "iam.account.deleted"
}

/**
 * Event published for security audit trail persistence (FR-015).
 * Consumed by system-admin-service audit module for immutable storage.
 */
data class AuditEvent(
    val userId: Long?,
    val action: String,
    val entityType: String?,
    val entityId: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val details: String?
) : DomainEvent {
    override val eventType: String = "iam.audit.event"
}
