package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.impl.DefaultClaims
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Unit tests for AudienceClaimValidator — validates JWT `aud` claim.
 * Feature-flagged: disabled when audience config is empty.
 *
 * FR-004: Claim validation pipeline — audience validation.
 * FR-010: Audience claim configuration (feature flag pattern).
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("AudienceClaimValidator Tests")
class AudienceClaimValidatorTest {

    @Mock private lateinit var securityProperties: SecurityProperties

    private fun createValidator(audience: String): AudienceClaimValidator {
        val jwtProps = SecurityProperties.JwtProperties(audience = audience)
        whenever(securityProperties.jwt).thenReturn(jwtProps)
        return AudienceClaimValidator(securityProperties)
    }

    @Test
    @DisplayName("TC01: Disabled (audience config empty) returns PASS — skip validation")
    fun shouldPassWhenAudienceDisabled() {
        // Given — audience is empty (disabled)
        val validator = createValidator("")
        val claims = DefaultClaims(mapOf(Claims.SUBJECT to "42"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
        assertEquals("AudienceClaimValidator", result.validatorName)
    }

    @Test
    @DisplayName("TC02: Enabled + matching audience returns PASS")
    fun shouldPassWhenAudienceMatches() {
        // Given — audience configured and token matches
        val validator = createValidator("my-app")
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.AUDIENCE to setOf("my-app")
        ))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
    }

    @Test
    @DisplayName("TC03: Enabled + mismatched audience returns FAIL")
    fun shouldFailWhenAudienceMismatches() {
        // Given
        val validator = createValidator("my-app")
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.AUDIENCE to setOf("other-app")
        ))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertEquals("AudienceClaimValidator", result.validatorName)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("expected=my-app"))
    }

    @Test
    @DisplayName("TC04: Enabled + null audience claim in token returns FAIL")
    fun shouldFailWhenAudienceClaimIsNull() {
        // Given — audience configured but no aud claim in token
        val validator = createValidator("my-app")
        val claims = DefaultClaims(mapOf(Claims.SUBJECT to "42"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertNotNull(result.reason)
    }

    @Test
    @DisplayName("TC05: Enabled + audience list in token contains expected value returns PASS")
    fun shouldPassWhenAudienceListContainsExpected() {
        // Given — token has multiple audiences, one matches
        val validator = createValidator("my-app")
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.AUDIENCE to setOf("other-app", "my-app", "third-app")
        ))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
    }
}
