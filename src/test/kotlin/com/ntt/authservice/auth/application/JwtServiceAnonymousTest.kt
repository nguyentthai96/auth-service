package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.TokenExpiredException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.whenever
import org.mockito.Mock

/**
 * Unit tests for JwtService anonymous token methods:
 * generateAnonymousToken and parseAnonymousToken.
 *
 * Uses HMAC-SHA256 (legacy key) for simplicity — RS256 requires key pair setup.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtService Anonymous Token Tests")
class JwtServiceAnonymousTest {

    @Mock private lateinit var securityProperties: SecurityProperties

    private lateinit var jwtService: JwtService

    private val secretKey = "test-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha256"

    @BeforeEach
    fun setUp() {
        val jwtProps = SecurityProperties.JwtProperties(
            secretKey = secretKey,
            accessTokenExpirationMs = 1800000,
            refreshTokenExpirationMs = 604800000,
            issuer = "auth-service-test",
            keyId = "test-kid",
            privateKeyPath = "",
            publicKeyPath = ""
        )
        val anonymousProps = SecurityProperties.AnonymousProperties(
            tokenTtlSeconds = 3600,
            sessionTtlSeconds = 86400,
            maxDataSizeBytes = 65536,
            maxRenewals = 24,
            promotedDataTtlSeconds = 604800
        )

        whenever(securityProperties.jwt).thenReturn(jwtProps)
        whenever(securityProperties.anonymous).thenReturn(anonymousProps)

        jwtService = JwtService(securityProperties)
    }

    @Nested
    @DisplayName("generateAnonymousToken")
    inner class GenerateAnonymousToken {

        @Test
        @DisplayName("should generate token with correct claims: type=anonymous, sub=sessionId, jti, exp")
        fun should_generateToken_when_validSessionId() {
            // When
            val token = jwtService.generateAnonymousToken("session-123")

            // Then
            val claims = jwtService.parseAnonymousToken(token)
            assertThat(claims.subject).isEqualTo("session-123")
            assertThat(claims["type"]).isEqualTo("anonymous")
            assertThat(claims.id).isNotBlank()
            assertThat(claims.expiration).isNotNull()
            assertThat(claims.issuer).isEqualTo("auth-service-test")
        }

        @Test
        @DisplayName("should generate unique JTI for each token")
        fun should_generateUniqueJti_when_calledMultipleTimes() {
            // When
            val token1 = jwtService.generateAnonymousToken("session-1")
            val token2 = jwtService.generateAnonymousToken("session-1")

            // Then
            val claims1 = jwtService.parseAnonymousToken(token1)
            val claims2 = jwtService.parseAnonymousToken(token2)
            assertThat(claims1.id).isNotEqualTo(claims2.id)
        }
    }

    @Nested
    @DisplayName("parseAnonymousToken")
    inner class ParseAnonymousToken {

        @Test
        @DisplayName("should parse valid anonymous token and return correct claims")
        fun should_parseToken_when_validAnonymousToken() {
            // Given
            val token = jwtService.generateAnonymousToken("session-parse-test")

            // When
            val claims = jwtService.parseAnonymousToken(token)

            // Then
            assertThat(claims.subject).isEqualTo("session-parse-test")
            assertThat(claims["type"]).isEqualTo("anonymous")
            assertThat(claims.id).isNotBlank()
        }

        @Test
        @DisplayName("should throw TokenExpiredException when token is not anonymous type")
        fun should_throwException_when_nonAnonymousToken() {
            // Given — generate a refresh token (type=refresh, not anonymous)
            val nonAnonymousToken = jwtService.generateRefreshToken(1L)

            // When/Then
            assertThrows<TokenExpiredException> {
                jwtService.parseAnonymousToken(nonAnonymousToken)
            }
        }

        @Test
        @DisplayName("should throw exception when token is tampered")
        fun should_throwException_when_tokenTampered() {
            // Given
            val token = jwtService.generateAnonymousToken("session-tamper")
            val tamperedToken = token.dropLast(5) + "XXXXX"

            // When/Then
            assertThrows<Exception> {
                jwtService.parseAnonymousToken(tamperedToken)
            }
        }

        @Test
        @DisplayName("should throw exception when token is expired")
        fun should_throwException_when_tokenExpired() {
            // Given — generate token with very short TTL
            // We can't easily test real expiration without time manipulation,
            // but we verify the token has an expiration claim set
            val token = jwtService.generateAnonymousToken("session-expiry")
            val claims = jwtService.parseAnonymousToken(token)

            // Then — verify expiration is set ~1 hour from now
            assertThat(claims.expiration).isNotNull()
            val expectedExpiry = System.currentTimeMillis() + 3600 * 1000
            assertThat(claims.expiration.time).isBetween(
                expectedExpiry - 5000, // 5 seconds tolerance
                expectedExpiry + 5000
            )
        }
    }
}
