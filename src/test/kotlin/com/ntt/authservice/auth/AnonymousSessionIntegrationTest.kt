package com.ntt.authservice.auth

import com.ntt.authservice.auth.application.AnonymousRateLimitService
import com.ntt.authservice.auth.application.AnonymousSessionResult
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.command.AnonymousSessionHandler
import com.ntt.authservice.auth.application.command.CreateAnonymousSessionCommand
import com.ntt.authservice.auth.application.command.RenewAnonymousTokenCommand
import com.ntt.authservice.auth.application.command.RenewAnonymousTokenHandler
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousRateLimitedException
import com.ntt.authservice.shared.exception.AnonymousSessionExpiredException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Integration-style tests for anonymous session lifecycle:
 * create session, rate limiting, renew token, expired session.
 *
 * Uses Mockito for Redis (no Testcontainers dependency) with real JwtService behavior mocked.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Anonymous Session Integration Tests")
class AnonymousSessionIntegrationTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var anonymousRateLimitService: AnonymousRateLimitService
    @Mock private lateinit var tokenStore: com.ntt.authservice.auth.application.port.out.TokenStore
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var valueOps: ValueOperations<String, String>
    @Mock private lateinit var hashOps: HashOperations<String, String, String>

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var sessionHandler: AnonymousSessionHandler
    private lateinit var renewHandler: RenewAnonymousTokenHandler

    private val anonymousProps = SecurityProperties.AnonymousProperties(
        tokenTtlSeconds = 3600,
        sessionTtlSeconds = 86400,
        maxDataSizeBytes = 65536,
        maxRenewals = 24,
        promotedDataTtlSeconds = 604800
    )

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        whenever(securityProperties.anonymous).thenReturn(anonymousProps)
        whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        sessionHandler = AnonymousSessionHandler(
            anonymousRateLimitService, jwtService, redisTemplate, securityProperties, meterRegistry
        )
        renewHandler = RenewAnonymousTokenHandler(
            jwtService, redisTemplate, tokenStore, securityProperties, meterRegistry
        )
    }

    @Nested
    @DisplayName("TC-001: Create anonymous session")
    inner class CreateSession {

        @Test
        @DisplayName("should create session and return token with correct structure")
        fun should_createSessionAndReturnToken_when_validRequest() {
            // Given
            whenever(jwtService.generateAnonymousToken(any())).thenReturn("eyJ.test.token")

            val command = CreateAnonymousSessionCommand(
                ipAddress = "192.168.1.1",
                deviceFingerprint = "fp-abc123"
            )

            // When
            val result = sessionHandler.handle(command)

            // Then
            assertThat(result).isNotNull
            assertThat(result.token).isEqualTo("eyJ.test.token")
            assertThat(result.sessionId).isNotBlank()
            assertThat(result.expiresIn).isEqualTo(3600L)

            // Verify Redis session created via pipeline
            verify(redisTemplate).executePipelined(any<org.springframework.data.redis.core.RedisCallback<*>>())

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.sessions.created").count()).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("TC-002: Rate limit anonymous session creation")
    inner class RateLimitSession {

        @Test
        @DisplayName("should throw AnonymousRateLimitedException when rate limit exceeded")
        fun should_throwRateLimited_when_exceededLimit() {
            // Given
            whenever(anonymousRateLimitService.checkRateLimit("10.0.0.1"))
                .thenThrow(AnonymousRateLimitedException(retryAfterSeconds = 3600))

            val command = CreateAnonymousSessionCommand(
                ipAddress = "10.0.0.1",
                deviceFingerprint = null
            )

            // When/Then
            assertThrows<AnonymousRateLimitedException> {
                sessionHandler.handle(command)
            }
        }
    }

    @Nested
    @DisplayName("TC-007/TC-008: Renew anonymous token")
    inner class RenewToken {

        @Test
        @DisplayName("should renew token with same sessionId and new JTI")
        fun should_renewToken_when_validTokenAndSessionExists() {
            // Given
            val claims = mock<io.jsonwebtoken.Claims>()
            whenever(claims.subject).thenReturn("session-123")
            whenever(claims.id).thenReturn("old-jti-456")
            whenever(jwtService.parseAnonymousToken("old-token")).thenReturn(claims)
            whenever(redisTemplate.hasKey("anon:session:session-123")).thenReturn(true)
            whenever(hashOps.get("anon:session:session-123", "renewalCount")).thenReturn("2")
            whenever(jwtService.generateAnonymousToken("session-123")).thenReturn("new-token-789")

            val command = RenewAnonymousTokenCommand(currentToken = "old-token")

            // When
            val result = renewHandler.handle(command)

            // Then
            assertThat(result.token).isEqualTo("new-token-789")
            assertThat(result.sessionId).isEqualTo("session-123")
            assertThat(result.expiresIn).isEqualTo(3600L)

            // Verify old JTI blacklisted
            verify(tokenStore).blacklistToken(eq("old-jti-456"), eq(0L), eq("RENEWAL"), any())

            // Verify renewal count incremented
            verify(hashOps).put("anon:session:session-123", "renewalCount", "3")

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.sessions.renewed").count()).isEqualTo(1.0)
        }

        @Test
        @DisplayName("should throw AnonymousSessionExpiredException when session not found")
        fun should_throwExpired_when_sessionNotFound() {
            // Given
            val claims = mock<io.jsonwebtoken.Claims>()
            whenever(claims.subject).thenReturn("expired-session")
            whenever(jwtService.parseAnonymousToken("expired-token")).thenReturn(claims)
            whenever(redisTemplate.hasKey("anon:session:expired-session")).thenReturn(false)

            val command = RenewAnonymousTokenCommand(currentToken = "expired-token")

            // When/Then
            assertThrows<AnonymousSessionExpiredException> {
                renewHandler.handle(command)
            }
        }
    }

    @Nested
    @DisplayName("TC-010-012: Token generation metrics")
    inner class TokenMetrics {

        @Test
        @DisplayName("should record token generation duration metric")
        fun should_recordTimerMetric_when_tokenGenerated() {
            // Given
            whenever(jwtService.generateAnonymousToken(any())).thenReturn("eyJ.test.token")

            val command = CreateAnonymousSessionCommand(
                ipAddress = "192.168.1.1",
                deviceFingerprint = null
            )

            // When
            sessionHandler.handle(command)

            // Then
            val timer = meterRegistry.timer("auth.anonymous.token.generation.duration")
            assertThat(timer.count()).isEqualTo(1)
        }
    }
}
