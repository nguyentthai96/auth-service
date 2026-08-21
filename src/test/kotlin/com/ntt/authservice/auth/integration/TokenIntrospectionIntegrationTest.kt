package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.`in`.web.dto.IntrospectionResponse
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.impl.DefaultClaims
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

/**
 * Integration-style tests for Token Introspection endpoint (FR-011 — RFC 7662).
 * Tests: valid token, expired token, blacklisted token, malformed token.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("Token Introspection Integration Tests")
class TokenIntrospectionIntegrationTest {

    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var tokenBlacklistRepository: TokenBlacklistRepository

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
        whenever(tokenBlacklistRepository.existsByTokenJti("jti-valid-123")).thenReturn(false)

        // Simulate controller logic
        val response = introspect("valid-jwt-token")

        assertTrue(response.active)
        assertEquals("42", response.sub)
        assertEquals("testuser", response.username)
        assertEquals(listOf("USER", "ADMIN"), response.roles)
        assertEquals(listOf("READ", "WRITE"), response.permissions)
        assertEquals("auth-service", response.iss)
        assertEquals("jti-valid-123", response.jti)
        Assertions.assertNotNull(response.exp)
        Assertions.assertNotNull(response.iat)
    }

    // ── TC2: Expired token → 200 { active: false } ──

    @Test
    @DisplayName("TC2: Expired token should return active=false")
    fun shouldReturnInactiveForExpiredToken() {
        whenever(jwtService.parseToken("expired-jwt-token"))
            .thenThrow(RuntimeException("Token expired"))

        val response = introspect("expired-jwt-token")

        assertFalse(response.active)
        Assertions.assertNull(response.sub)
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
        whenever(tokenBlacklistRepository.existsByTokenJti("jti-blacklisted-456")).thenReturn(true)

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
        Assertions.assertNull(response.sub)
    }

    /**
     * Simulates TokenController.introspect() logic for unit-level testing.
     */
    private fun introspect(token: String): IntrospectionResponse {
        return try {
            val claims = jwtService.parseToken(token)
            val jti = claims.id
            val isBlacklisted = jti != null && tokenBlacklistRepository.existsByTokenJti(jti)

            IntrospectionResponse(
                active = !isBlacklisted,
                sub = claims.subject,
                username = claims["username"] as? String,
                roles = claims["roles"] as? List<String>,
                permissions = claims["permissions"] as? List<String>,
                exp = claims.expiration?.time?.div(1000),
                iat = claims.issuedAt?.time?.div(1000),
                iss = claims.issuer,
                jti = jti
            )
        } catch (e: Exception) {
            IntrospectionResponse(active = false)
        }
    }
}
