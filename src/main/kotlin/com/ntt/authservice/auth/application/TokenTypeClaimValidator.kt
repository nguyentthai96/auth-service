package com.ntt.authservice.auth.application

import io.jsonwebtoken.Claims
import org.springframework.stereotype.Component

/**
 * Validates the JWT `type` claim to prevent non-access tokens from being used as access tokens.
 * Allowed types: null (standard access token), "access", "anonymous".
 * Rejected types: "mfa", "refresh" — these token types have different security scopes.
 *
 * FR-004: Claim validation pipeline — token type validation.
 * Replaces inline type check previously in JwtAuthFilter L57-77.
 */
@Component
class TokenTypeClaimValidator : ClaimValidator {

    override val name: String = "TokenTypeClaimValidator"

    companion object {
        private val ALLOWED_TYPES = setOf(null, "access", "anonymous")
    }

    override fun validate(claims: Claims): ClaimValidationResult {
        val tokenType = claims["type"] as? String

        return if (tokenType in ALLOWED_TYPES) {
            ClaimValidationResult(name, ClaimValidationStatus.PASS)
        } else {
            ClaimValidationResult(
                name,
                ClaimValidationStatus.FAIL,
                "Token type '$tokenType' not allowed as access token"
            )
        }
    }
}
