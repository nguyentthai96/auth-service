package com.ntt.authservice.auth.domain.model.vo

/**
 * Strongly-typed email value object with basic validation.
 */
@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "Email must contain '@': $value" }
        require(value.isNotBlank()) { "Email must not be blank" }
    }
}
