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
 * Unit tests for IssuerClaimValidator — validates JWT `iss` claim.
 *
 * FR-004: Claim validation pipeline — issuer validation.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("IssuerClaimValidator Tests")
class IssuerClaimValidatorTest {

    @Mock private lateinit var securityProperties: SecurityProperties

    private lateinit var validator: IssuerClaimValidator

    @BeforeEach
    fun setUp() {
        val jwtProps = SecurityProperties.JwtProperties(issuer = "auth-service")
        whenever(securityProperties.jwt).thenReturn(jwtProps)
        validator = IssuerClaimValidator(securityProperties)
    }

    @Test
    @DisplayName("TC01: Matching issuer returns PASS")
    fun shouldPassWhenIssuerMatches() {
        // Given
        val claims = DefaultClaims(mapOf(Claims.ISSUER to "auth-service"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
        assertEquals("IssuerClaimValidator", result.validatorName)
        assertNull(result.reason)
    }

    @Test
    @DisplayName("TC02: Mismatched issuer returns FAIL with descriptive reason")
    fun shouldFailWhenIssuerMismatches() {
        // Given
        val claims = DefaultClaims(mapOf(Claims.ISSUER to "other-service"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertEquals("IssuerClaimValidator", result.validatorName)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("expected=auth-service"))
        assertTrue(result.reason!!.contains("actual=other-service"))
    }

    @Test
    @DisplayName("TC03: Null issuer in claims returns FAIL")
    fun shouldFailWhenIssuerIsNull() {
        // Given — no issuer claim in map
        val claims = DefaultClaims(mapOf(Claims.SUBJECT to "42"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertEquals("IssuerClaimValidator", result.validatorName)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("actual=null"))
    }
}
