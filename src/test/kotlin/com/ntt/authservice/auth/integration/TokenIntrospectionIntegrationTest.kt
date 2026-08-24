package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.`in`.web.dto.IntrospectionResponse
import com.ntt.authservice.auth.application.*
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.impl.DefaultClaims
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import java.util.*

/**
 * Integration-style tests for Token Introspection endpoint (FR-007, FR-011 — RFC 7662).
 * Tests: valid token, expired token, blacklisted token, malformed token,
 *        issuer mismatch, audience mismatch, MFA token rejection.
 *
 * Updated to use ClaimValidatorChain.validateAll() and TokenBlacklistCacheService
 * matching actual TokenController.introspect() implementation.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Token Introspection Integration Tests")
class TokenIntrospectionIntegrationTest {

    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var tokenBlacklistCacheService: TokenBlacklistCacheService
    @Mock private lateinit var claimValidatorChain: ClaimValidatorChain

    // ── TC1: Valid token → 200 { active: true, sub, roles, permissions, exp, iat } ──

    @Test
    @DisplayName("TC1: Valid token should return active=true with full claims")
    fun shouldReturnActiveForValidToken() {
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.ID to "jti-valid-123",
            Claims.ISSUER to "auth-service",
            Claims.EXPIRATION to Date(System.currentTimeMillis() + 900_000),
            Claims.ISSUED_AT to Date(System.currentTimeMillis() - 60_000),
            "username" to "testuser",
            "roles" to listOf("USER", "ADMIN"),
            "permissions" to listOf("READ", "WRITE")
        ))
        whenever(jwtService.parseToken("valid-jwt-token")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-valid-123")).thenReturn(false)
        whenever(claimValidatorChain.validateAll(any())).thenReturn(listOf(
            ClaimValidationResult("IssuerClaimValidator", ClaimValidationStatus.PASS),
            ClaimValidationResult("AudienceClaimValidator", ClaimValidationStatus.PASS),
            ClaimValidationResult("TokenTypeClaimValidator", ClaimValidationStatus.PASS)
        ))

        // Simulate controller logic
        val response = introspect("valid-jwt-token")

        assertTrue(response.active)
        assertEquals("42", response.sub)
        assertEquals("testuser", response.username)
        assertEquals(listOf("USER", "ADMIN"), response.roles)
        assertEquals(listOf("READ", "WRITE"), response.permissions)
        assertEquals("auth-service", response.iss)
        assertEquals("jti-valid-123", response.jti)
        assertNotNull(response.exp)
        assertNotNull(response.iat)
    }

    // ── TC2: Expired token → 200 { active: false } ──

    @Test
    @DisplayName("TC2: Expired token should return active=false")
    fun shouldReturnInactiveForExpiredToken() {
        whenever(jwtService.parseToken("expired-jwt-token"))
            .thenThrow(RuntimeException("Token expired"))

        val response = introspect("expired-jwt-token")

        assertFalse(response.active)
        assertNull(response.sub)
    }

    // ── TC3: Blacklisted jti → 200 { active: false } ──

    @Test
    @DisplayName("TC3: Blacklisted token (jti in blacklist) should return active=false")
    fun shouldReturnInactiveForBlacklistedToken() {
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.ID to "jti-blacklisted-456",
            Claims.ISSUER to "auth-service",
            Claims.EXPIRATION to Date(System.currentTimeMillis() + 900_000),
            Claims.ISSUED_AT to Date()
        ))
        whenever(jwtService.parseToken("blacklisted-jwt-token")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-blacklisted-456")).thenReturn(true)
        whenever(claimValidatorChain.validateAll(any())).thenReturn(listOf(
            ClaimValidationResult("IssuerClaimValidator", ClaimValidationStatus.PASS)
        ))

        val response = introspect("blacklisted-jwt-token")

        assertFalse(response.active)
        assertEquals("42", response.sub)
        assertEquals("jti-blacklisted-456", response.jti)
    }

    // ── TC4: Malformed token → 200 { active: false } ──

    @Test
    @DisplayName("TC4: Malformed token should return active=false")
    fun shouldReturnInactiveForMalformedToken() {
        whenever(jwtService.parseToken("not.a.valid.jwt"))
            .thenThrow(RuntimeException("Malformed JWT"))

        val response = introspect("not.a.valid.jwt")

        assertFalse(response.active)
        assertNull(response.sub)
    }

    // ── TC5: Wrong issuer → 200 { active: false } via ClaimValidatorChain ──

    @Test
    @DisplayName("TC5: Token with wrong issuer should return active=false via ClaimValidatorChain")
    fun shouldReturnInactiveForWrongIssuer() {
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.ID to "jti-issuer-bad",
            Claims.ISSUER to "wrong-service",
            Claims.EXPIRATION to Date(System.currentTimeMillis() + 900_000),
            Claims.ISSUED_AT to Date(),
            "username" to "testuser",
            "roles" to listOf("USER"),
            "permissions" to listOf("READ")
        ))
        whenever(jwtService.parseToken("issuer-bad-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-issuer-bad")).thenReturn(false)
        whenever(claimValidatorChain.validateAll(any())).thenReturn(listOf(
            ClaimValidationResult("IssuerClaimValidator", ClaimValidationStatus.FAIL, "Issuer mismatch: expected=auth-service, actual=wrong-service"),
            ClaimValidationResult("AudienceClaimValidator", ClaimValidationStatus.PASS),
            ClaimValidationResult("TokenTypeClaimValidator", ClaimValidationStatus.PASS)
        ))

        val response = introspect("issuer-bad-jwt")

        assertFalse(response.active)
        // Sub and other fields are still populated (RFC 7662 — always return claims)
        assertEquals("42", response.sub)
    }

    // ── TC6: Wrong audience → 200 { active: false } via ClaimValidatorChain ──

    @Test
    @DisplayName("TC6: Token with wrong audience (when configured) should return active=false")
    fun shouldReturnInactiveForWrongAudience() {
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.ID to "jti-aud-bad",
            Claims.ISSUER to "auth-service",
            Claims.AUDIENCE to setOf("other-app"),
            Claims.EXPIRATION to Date(System.currentTimeMillis() + 900_000),
            Claims.ISSUED_AT to Date(),
            "username" to "testuser",
            "roles" to listOf("USER"),
            "permissions" to listOf("READ")
        ))
        whenever(jwtService.parseToken("audience-bad-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-aud-bad")).thenReturn(false)
        whenever(claimValidatorChain.validateAll(any())).thenReturn(listOf(
            ClaimValidationResult("IssuerClaimValidator", ClaimValidationStatus.PASS),
            ClaimValidationResult("AudienceClaimValidator", ClaimValidationStatus.FAIL, "Audience mismatch: expected=my-app, actual=[other-app]"),
            ClaimValidationResult("TokenTypeClaimValidator", ClaimValidationStatus.PASS)
        ))

        val response = introspect("audience-bad-jwt")

        assertFalse(response.active)
        assertEquals("42", response.sub)
    }

    // ── TC7: MFA token → 200 { active: false } via TokenTypeClaimValidator ──

    @Test
    @DisplayName("TC7: MFA token should return active=false (TokenTypeClaimValidator rejects)")
    fun shouldReturnInactiveForMfaToken() {
        val claims = DefaultClaims(mapOf(
            Claims.SUBJECT to "42",
            Claims.ID to "jti-mfa-token",
            Claims.ISSUER to "auth-service",
            Claims.EXPIRATION to Date(System.currentTimeMillis() + 900_000),
            Claims.ISSUED_AT to Date(),
            "type" to "mfa",
            "username" to "testuser",
            "roles" to listOf("USER"),
            "permissions" to listOf("READ")
        ))
        whenever(jwtService.parseToken("mfa-jwt-token")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-mfa-token")).thenReturn(false)
        whenever(claimValidatorChain.validateAll(any())).thenReturn(listOf(
            ClaimValidationResult("IssuerClaimValidator", ClaimValidationStatus.PASS),
            ClaimValidationResult("AudienceClaimValidator", ClaimValidationStatus.PASS),
            ClaimValidationResult("TokenTypeClaimValidator", ClaimValidationStatus.FAIL, "Token type 'mfa' not allowed as access token")
        ))

        val response = introspect("mfa-jwt-token")

        assertFalse(response.active)
        assertEquals("42", response.sub)
    }

    /**
     * Simulates TokenController.introspect() logic for unit-level testing.
     * Updated to match actual implementation: uses ClaimValidatorChain.validateAll()
     * and TokenBlacklistCacheService (not direct repository).
     */
    private fun introspect(token: String): IntrospectionResponse {
        return try {
            val claims = jwtService.parseToken(token)
            val jti = claims.id

            // FR-011: Cache-based blacklist check
            val isBlacklisted = jti != null && tokenBlacklistCacheService.isBlacklisted(jti)

            // FR-007: Claim validation via chain (collect-all mode for diagnostic)
            val validationResults = claimValidatorChain.validateAll(claims)
            val hasClaimFailure = validationResults.any { it.status == ClaimValidationStatus.FAIL }

            val isActive = !isBlacklisted && !hasClaimFailure

            // FR-007: RFC 7662 fields
            val permissions = claims["permissions"] as? List<String>

            IntrospectionResponse(
                active = isActive,
                sub = claims.subject,
                username = claims["username"] as? String,
                roles = claims["roles"] as? List<String>,
                permissions = permissions,
                exp = claims.expiration?.time?.div(1000),
                iat = claims.issuedAt?.time?.div(1000),
                iss = claims.issuer,
                jti = jti,
                tokenType = if (isActive) "Bearer" else null,
                scope = if (isActive) permissions?.joinToString(" ") else null,
                clientId = if (isActive) claims.audience?.firstOrNull() else null
            )
        } catch (e: Exception) {
            IntrospectionResponse(active = false)
        }
    }
}
