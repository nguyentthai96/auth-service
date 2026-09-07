package com.ntt.authservice.shared.security

import com.ntt.authservice.auth.application.ClaimValidationException
import com.ntt.authservice.auth.application.ClaimValidatorChain
import com.ntt.authservice.auth.application.FingerprintService
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.TokenBlacklistCacheService
import com.ntt.authservice.auth.application.event.TokenEventRecorder
import com.ntt.authservice.auth.domain.event.TokenValidationFailedEvent
import com.ntt.authservice.auth.domain.event.ValidationFailureReason
import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.impl.DefaultClaims
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Unit tests for JwtAuthFilter — JWT validation + blacklist + claim chain + event recording + metrics.
 *
 * FR-003: Cache-based blacklist check.
 * FR-009: ClaimValidatorChain integration.
 * FR-012: Validation failure event recording.
 * OBS-002: Micrometer metrics verification.
 * BUG-001: mapValidatorToReason fix verification.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthFilter Tests")
class JwtAuthFilterTest {

    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var tokenBlacklistCacheService: TokenBlacklistCacheService
    @Mock private lateinit var claimValidatorChain: ClaimValidatorChain
    @Mock private lateinit var tokenEventRecorder: TokenEventRecorder
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var fingerprintService: FingerprintService

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var filter: JwtAuthFilter
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        SecurityContextHolder.clearContext()

        filter = JwtAuthFilter(
            jwtService,
            tokenBlacklistCacheService,
            claimValidatorChain,
            tokenEventRecorder,
            fingerprintService,
            securityProperties,
            meterRegistry
        )

        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        filterChain = mock<FilterChain>()
    }

    private fun validClaims(
        subject: String = "42",
        jti: String = "jti-valid-123",
        roles: List<String> = listOf("USER"),
        permissions: List<String> = listOf("read"),
        type: String? = null
    ): Claims {
        val map = mutableMapOf<String, Any>(
            Claims.SUBJECT to subject,
            Claims.ID to jti,
            "roles" to roles,
            "permissions" to permissions,
            "username" to "testuser",
            "active_domain" to "default",
            "domains" to listOf("default")
        )
        if (type != null) map["type"] = type
        return DefaultClaims(map)
    }

    @Test
    @DisplayName("TC01: No Authorization header → filterChain.doFilter() called, SecurityContext empty")
    fun shouldContinueWithoutAuthWhenNoHeader() {
        // Given — no auth header
        // When
        filter.doFilter(request, response, filterChain)

        // Then
        verify(filterChain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    @DisplayName("TC02: Valid token → SecurityContext set with correct authorities (roles + permissions)")
    fun shouldSetSecurityContextForValidToken() {
        // Given
        request.addHeader("Authorization", "Bearer valid-jwt")
        val claims = validClaims(roles = listOf("USER", "ADMIN"), permissions = listOf("read", "write"))
        whenever(jwtService.parseToken("valid-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-valid-123")).thenReturn(false)

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("42", auth!!.principal)

        val authorityNames = auth.authorities.map { it.authority }
        assertTrue(authorityNames.contains("ROLE_USER"))
        assertTrue(authorityNames.contains("ROLE_ADMIN"))
        assertTrue(authorityNames.contains("PERM_read"))
        assertTrue(authorityNames.contains("PERM_write"))

        verify(filterChain).doFilter(request, response)
    }

    @Test
    @DisplayName("TC03: Blacklisted token → response 401, tokenEventRecorder.recordValidationFailure() called with BLACKLISTED")
    fun shouldReturn401ForBlacklistedToken() {
        // Given
        request.addHeader("Authorization", "Bearer blacklisted-jwt")
        val claims = validClaims(jti = "jti-blacklisted")
        whenever(jwtService.parseToken("blacklisted-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted("jti-blacklisted")).thenReturn(true)

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        assertEquals(401, response.status)
        verify(tokenEventRecorder).recordValidationFailure(
            argThat<TokenValidationFailedEvent> { reason == ValidationFailureReason.BLACKLISTED },
            anyOrNull()
        )
        verify(filterChain, never()).doFilter(any(), any())
    }

    @Test
    @DisplayName("TC04: Signature invalid → event recorded SIGNATURE_INVALID, filterChain continues")
    fun shouldRecordSignatureInvalidAndContinue() {
        // Given
        request.addHeader("Authorization", "Bearer bad-sig-jwt")
        whenever(jwtService.parseToken("bad-sig-jwt"))
            .thenThrow(io.jsonwebtoken.security.SignatureException("Invalid signature"))

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        verify(tokenEventRecorder).recordValidationFailure(
            argThat<TokenValidationFailedEvent> { reason == ValidationFailureReason.SIGNATURE_INVALID },
            anyOrNull()
        )
        verify(filterChain).doFilter(request, response)
    }

    @Test
    @DisplayName("TC05: Issuer mismatch → event recorded ISSUER_MISMATCH — verifies BUG-001 fix")
    fun shouldRecordIssuerMismatchForIssuerValidatorFailure() {
        // Given
        request.addHeader("Authorization", "Bearer issuer-bad-jwt")
        val claims = validClaims()
        whenever(jwtService.parseToken("issuer-bad-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted(any())).thenReturn(false)
        whenever(claimValidatorChain.validateOrThrow(any()))
            .thenThrow(ClaimValidationException("IssuerClaimValidator", "Issuer mismatch"))

        // When
        filter.doFilter(request, response, filterChain)

        // Then — BUG-001 FIX: should be ISSUER_MISMATCH, NOT SIGNATURE_INVALID
        verify(tokenEventRecorder).recordValidationFailure(
            argThat<TokenValidationFailedEvent> { reason == ValidationFailureReason.ISSUER_MISMATCH },
            anyOrNull()
        )
        verify(filterChain).doFilter(request, response)
    }

    @Test
    @DisplayName("TC06: Audience mismatch → event recorded AUDIENCE_MISMATCH")
    fun shouldRecordAudienceMismatch() {
        // Given
        request.addHeader("Authorization", "Bearer aud-bad-jwt")
        val claims = validClaims()
        whenever(jwtService.parseToken("aud-bad-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted(any())).thenReturn(false)
        whenever(claimValidatorChain.validateOrThrow(any()))
            .thenThrow(ClaimValidationException("AudienceClaimValidator", "Audience mismatch"))

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        verify(tokenEventRecorder).recordValidationFailure(
            argThat<TokenValidationFailedEvent> { reason == ValidationFailureReason.AUDIENCE_MISMATCH },
            anyOrNull()
        )
    }

    @Test
    @DisplayName("TC07: Type rejected → event recorded TYPE_REJECTED")
    fun shouldRecordTypeRejected() {
        // Given
        request.addHeader("Authorization", "Bearer type-bad-jwt")
        val claims = validClaims()
        whenever(jwtService.parseToken("type-bad-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted(any())).thenReturn(false)
        whenever(claimValidatorChain.validateOrThrow(any()))
            .thenThrow(ClaimValidationException("TokenTypeClaimValidator", "Type rejected"))

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        verify(tokenEventRecorder).recordValidationFailure(
            argThat<TokenValidationFailedEvent> { reason == ValidationFailureReason.TYPE_REJECTED },
            anyOrNull()
        )
    }

    @Test
    @DisplayName("TC08: Anonymous token (type=anonymous) → authorities contain ROLE_ANONYMOUS")
    fun shouldSetRoleAnonymousForAnonymousToken() {
        // Given
        request.addHeader("Authorization", "Bearer anon-jwt")
        val claims = validClaims(type = "anonymous")
        whenever(jwtService.parseToken("anon-jwt")).thenReturn(claims)
        whenever(tokenBlacklistCacheService.isBlacklisted(any())).thenReturn(false)

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        val authorityNames = auth!!.authorities.map { it.authority }
        assertTrue(authorityNames.contains("ROLE_ANONYMOUS"))
        assertFalse(authorityNames.contains("ROLE_USER"))
    }

    @Test
    @DisplayName("TC09: Expired token → filterChain continues without auth, no event recorded (default behavior)")
    fun shouldContinueWithoutAuthForExpiredToken() {
        // Given
        request.addHeader("Authorization", "Bearer expired-jwt")
        whenever(jwtService.parseToken("expired-jwt"))
            .thenThrow(io.jsonwebtoken.ExpiredJwtException(null, null, "Token expired"))

        // When
        filter.doFilter(request, response, filterChain)

        // Then
        verify(filterChain).doFilter(request, response)
        assertNull(SecurityContextHolder.getContext().authentication)
        // ExpiredJwtException is NOT a SignatureException, so no specific event recorded
        // It falls through to generic catch block
    }

    @Test
    @DisplayName("TC10: Event recording throws exception → swallowed, filterChain continues normally")
    fun shouldSwallowEventRecordingException() {
        // Given
        request.addHeader("Authorization", "Bearer bad-sig-jwt-2")
        whenever(jwtService.parseToken("bad-sig-jwt-2"))
            .thenThrow(io.jsonwebtoken.security.SignatureException("Invalid signature"))
        whenever(tokenEventRecorder.recordValidationFailure(any(), anyOrNull()))
            .thenThrow(RuntimeException("Event store down"))

        // When — should NOT throw
        assertDoesNotThrow {
            filter.doFilter(request, response, filterChain)
        }

        // Then — filterChain still called
        verify(filterChain).doFilter(request, response)
    }

    @Nested
    @DisplayName("Metrics verification")
    inner class MetricsTests {

        @Test
        @DisplayName("TC11: SimpleMeterRegistry verifies auth.token.validation counter and duration timer")
        fun shouldRecordValidationMetrics() {
            // Given — valid token flow
            request.addHeader("Authorization", "Bearer metrics-jwt")
            val claims = validClaims()
            whenever(jwtService.parseToken("metrics-jwt")).thenReturn(claims)
            whenever(tokenBlacklistCacheService.isBlacklisted(any())).thenReturn(false)

            // When
            filter.doFilter(request, response, filterChain)

            // Then — success counter
            val successCounter = meterRegistry.find("auth.token.validation")
                .tag("result", "success").counter()
            assertNotNull(successCounter)
            assertEquals(1.0, successCounter!!.count())

            // Timer recorded
            val timer = meterRegistry.find("auth.token.validation.duration")
                .tag("result", "success").timer()
            assertNotNull(timer)
            assertEquals(1, timer!!.count())
        }
    }
}
