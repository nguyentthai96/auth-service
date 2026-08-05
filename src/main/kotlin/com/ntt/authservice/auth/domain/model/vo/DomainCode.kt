package com.ntt.authservice.auth.domain.model.vo

/**
 * Strongly-typed domain code identifier.
 */
@JvmInline
value class DomainCode(val value: String) {
    init {
        require(value.isNotBlank()) { "DomainCode must not be blank" }
    }
}
