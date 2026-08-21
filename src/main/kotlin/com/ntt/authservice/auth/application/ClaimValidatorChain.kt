package com.ntt.authservice.auth.application

import io.jsonwebtoken.Claims
import org.springframework.stereotype.Component

/**
 * Chain of responsibility for JWT claim validation.
 * Collects all ClaimValidator beans and provides two execution modes:
 * - validateOrThrow(): fail-fast for JwtAuthFilter hot path (FR-009)
 * - validateAll(): collect-all for introspection diagnostic path (FR-007)
 *
 * FR-004: Claim validation pipeline.
 */
@Component
class ClaimValidatorChain(
    private val validators: List<ClaimValidator>
) {

    /**
     * Fail-fast validation — stops at first failure and throws ClaimValidationException.
     * Used in JwtAuthFilter (hot path) for minimal overhead.
     */
    fun validateOrThrow(claims: Claims) {
        for (validator in validators) {
            val result = validator.validate(claims)
            if (result.status == ClaimValidationStatus.FAIL) {
                throw ClaimValidationException(
                    validatorName = result.validatorName,
                    message = result.reason ?: "Validation failed"
                )
            }
        }
    }

    /**
     * Collect-all validation — runs all validators and returns complete results list.
     * Used in TokenController.introspect() for diagnostic/RFC 7662 response.
     */
    fun validateAll(claims: Claims): List<ClaimValidationResult> {
        return validators.map { it.validate(claims) }
    }
}
