package com.ntt.authservice.auth.domain.event

import java.time.Instant

/**
 * Domain event raised when an account is unlocked (FR-007).
 * Published to Kafka for notification service consumption.
 */
data class AccountUnlockedEvent(
    val userId: Long,
    val username: String,
    val unlockedBy: UnlockSource,
    val performedBy: String? = null,
    val timestamp: Instant = Instant.now()
) {
    enum class UnlockSource {
        ADMIN,
        SELF_SERVICE,
        TTL_EXPIRED
    }
}
