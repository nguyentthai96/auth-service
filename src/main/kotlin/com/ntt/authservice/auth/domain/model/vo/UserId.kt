package com.ntt.authservice.auth.domain.model.vo

/**
 * Strongly-typed user identifier — prevents accidental mixing with other Long IDs.
 * value == 0 means unsaved entity (JPA will assign on persist).
 */
@JvmInline
value class UserId(val value: Long) {
    init {
        require(value >= 0) { "UserId must be non-negative" }
    }

    val isNew: Boolean get() = value == 0L
}
