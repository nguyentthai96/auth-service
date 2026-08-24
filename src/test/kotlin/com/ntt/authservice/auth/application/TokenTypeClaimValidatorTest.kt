package com.ntt.authservice.auth.application

import io.jsonwebtoken.impl.DefaultClaims
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for TokenTypeClaimValidator — validates JWT `type` claim.
 * Allowed types: null, "access", "anonymous". Rejected: "mfa", "refresh", unknown.
 *
 * FR-004: Claim validation pipeline — token type validation.
 */
@DisplayName("TokenTypeClaimValidator Tests")
class TokenTypeClaimValidatorTest {

    private val validator = TokenTypeClaimValidator()

    @Test
    @DisplayName("TC01: null type claim returns PASS (backward compatible — no type = access token)")
    fun shouldPassWhenTypeIsNull() {
        // Given — no type claim in token
        val claims = DefaultClaims(mapOf("sub" to "42"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
        assertEquals("TokenTypeClaimValidator", result.validatorName)
    }

    @Test
    @DisplayName("TC02: \"access\" type returns PASS")
    fun shouldPassWhenTypeIsAccess() {
        // Given
        val claims = DefaultClaims(mapOf("sub" to "42", "type" to "access"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
    }

    @Test
    @DisplayName("TC03: \"anonymous\" type returns PASS")
    fun shouldPassWhenTypeIsAnonymous() {
        // Given
        val claims = DefaultClaims(mapOf("sub" to "42", "type" to "anonymous"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.PASS, result.status)
    }

    @Test
    @DisplayName("TC04: \"mfa\" type returns FAIL — not allowed as access token")
    fun shouldFailWhenTypeIsMfa() {
        // Given
        val claims = DefaultClaims(mapOf("sub" to "42", "type" to "mfa"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("mfa"))
        assertTrue(result.reason!!.contains("not allowed"))
    }

    @Test
    @DisplayName("TC05: \"refresh\" type returns FAIL — not allowed as access token")
    fun shouldFailWhenTypeIsRefresh() {
        // Given
        val claims = DefaultClaims(mapOf("sub" to "42", "type" to "refresh"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("refresh"))
    }

    @Test
    @DisplayName("TC06: \"service\" (unknown) type returns FAIL")
    fun shouldFailWhenTypeIsUnknown() {
        // Given
        val claims = DefaultClaims(mapOf("sub" to "42", "type" to "service"))

        // When
        val result = validator.validate(claims)

        // Then
        assertEquals(ClaimValidationStatus.FAIL, result.status)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("service"))
    }
}
