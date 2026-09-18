package com.ntt.authservice.auth.domain.event

import java.time.Instant

/**
 * Domain event raised when an account is locked (FR-007).
 * Published to Kafka for notification service consumption.
 */
data class AccountLockedEvent(
    val userId: Long,
    val username: String,
    val email: String,
    val lockType: LockType,
    val reason: String,
    val lockedUntil: Instant?,
    val clientIp: String?,
    val unlockToken: String? = null,
    val timestamp: Instant = Instant.now()
) {
    enum class LockType {
        TEMPORARY,
        PERMANENT
    }
}
