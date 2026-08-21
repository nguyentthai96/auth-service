package com.ntt.authservice.auth.application

/**
 * Exception thrown by ClaimValidatorChain.validateOrThrow() on first validation failure.
 * Used in JwtAuthFilter for fail-fast claim validation on the hot path.
 *
 * FR-004: Claim validation pipeline — fail-fast mode.
 */
class ClaimValidationException(
    val validatorName: String,
    override val message: String
) : RuntimeException("Claim validation failed [$validatorName]: $message")
