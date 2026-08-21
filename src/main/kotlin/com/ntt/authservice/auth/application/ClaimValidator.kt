package com.ntt.authservice.auth.application

import io.jsonwebtoken.Claims

/**
 * Interface for JWT claim validators (chain of responsibility pattern).
 * Each validator checks a specific claim against expected values.
 *
 * FR-004: Claim validation pipeline.
 */
interface ClaimValidator {
    /** Human-readable name of this validator (used in logs and events). */
    val name: String

    /** Validate claims and return result. MUST NOT throw exceptions. */
    fun validate(claims: Claims): ClaimValidationResult
}
