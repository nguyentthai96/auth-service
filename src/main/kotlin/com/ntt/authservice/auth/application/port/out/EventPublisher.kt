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
