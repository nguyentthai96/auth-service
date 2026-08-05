package com.ntt.authservice.auth.domain.model.vo

/**
 * Strongly-typed password hash — prevents accidental use of raw passwords.
 */
@JvmInline
value class PasswordHash(val value: String) {
    init {
        require(value.isNotBlank()) { "PasswordHash must not be blank" }
    }
}
