package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Claims
import org.springframework.stereotype.Component

/**
 * Validates the JWT `iss` (issuer) claim against the configured issuer value.
 *
 * FR-004: Claim validation pipeline — issuer validation.
 * Replaces implicit issuer validation previously handled inside JJWT parser.
 */
@Component
class IssuerClaimValidator(
    private val securityProperties: SecurityProperties
) : ClaimValidator {

    override val name: String = "IssuerClaimValidator"

    override fun validate(claims: Claims): ClaimValidationResult {
        val expectedIssuer = securityProperties.jwt.issuer
        val actualIssuer = claims.issuer

        return if (actualIssuer == expectedIssuer) {
            ClaimValidationResult(name, ClaimValidationStatus.PASS)
        } else {
            ClaimValidationResult(
                name,
                ClaimValidationStatus.FAIL,
                "Issuer mismatch: expected=$expectedIssuer, actual=$actualIssuer"
            )
        }
    }
}
