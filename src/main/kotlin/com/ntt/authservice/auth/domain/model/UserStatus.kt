package com.ntt.authservice.auth.domain.model

import java.time.Instant

/**
 * Domain-level user status — replaces magic strings "ACTIVE", "LOCKED", "SUSPENDED".
 * Exhaustive matching enforced by sealed interface.
 */
sealed interface UserStatus {
    data object Active : UserStatus
    data class Locked(val until: Instant, val reason: String = "Too many failed login attempts") : UserStatus
    data object Suspended : UserStatus
    data object Inactive : UserStatus

    companion object {
        /**
         * Convert from legacy string status to domain sealed type.
         */
        fun fromString(status: String, lockedUntil: Instant? = null): UserStatus = when (status.uppercase()) {
            "ACTIVE" -> Active
            "LOCKED" -> Locked(lockedUntil ?: Instant.now(), "Account locked")
            "SUSPENDED" -> Suspended
            "INACTIVE" -> Inactive
            else -> throw IllegalArgumentException("Unknown user status: $status")
        }

        /**
         * Convert domain status to legacy string for persistence.
         */
        fun toDbString(status: UserStatus): String = when (status) {
            is Active -> "ACTIVE"
            is Locked -> "LOCKED"
            is Suspended -> "SUSPENDED"
            is Inactive -> "INACTIVE"
        }
    }
}
