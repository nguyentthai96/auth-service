package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Claims
import org.springframework.stereotype.Component

/**
 * Validates the JWT `aud` (audience) claim when audience is configured.
 * Feature-flagged: disabled when `app.security.jwt.audience` is empty (default).
 *
 * FR-004: Claim validation pipeline — audience validation.
 * FR-010: Audience claim configuration (feature flag pattern).
 *
 * When enabled, validates that the token's `aud` claim contains the configured audience value.
 * When disabled (empty string), always returns PASS for backward compatibility.
 */
@Component
class AudienceClaimValidator(
    private val securityProperties: SecurityProperties
) : ClaimValidator {

    override val name: String = "AudienceClaimValidator"

    override fun validate(claims: Claims): ClaimValidationResult {
        val expectedAudience = securityProperties.jwt.audience

        // Feature flag: disabled when audience is blank
        if (expectedAudience.isBlank()) {
            return ClaimValidationResult(name, ClaimValidationStatus.PASS)
        }

        val audience = claims.audience
        return if (audience != null && audience.contains(expectedAudience)) {
            ClaimValidationResult(name, ClaimValidationStatus.PASS)
        } else {
            ClaimValidationResult(
                name,
                ClaimValidationStatus.FAIL,
                "Audience mismatch: expected=$expectedAudience, actual=$audience"
            )
        }
    }
}
