package com.ntt.authservice.auth.application

/**
 * Result of a single claim validator execution.
 * Used by ClaimValidatorChain to aggregate validation outcomes.
 */
data class ClaimValidationResult(
    val validatorName: String,
    val status: ClaimValidationStatus,
    val reason: String? = null
)

/**
 * Status enum for claim validation results.
 */
enum class ClaimValidationStatus {
    PASS,
    FAIL
}
