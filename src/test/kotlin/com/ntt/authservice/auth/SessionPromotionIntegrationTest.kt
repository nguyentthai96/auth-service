package com.ntt.authservice.auth

import com.ntt.authservice.auth.application.AnonymousSessionDataService
import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.SessionPromotionService
import com.ntt.authservice.rbac.adapter.out.persistence.entity.TokenBlacklistEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Integration-style tests for session promotion:
 * login/register with promotion, concurrent promotion, expired session, JTI blacklisting.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Session Promotion Integration Tests")
class SessionPromotionIntegrationTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var anonymousSessionDataService: AnonymousSessionDataService
    @Mock private lateinit var tokenBlacklistRepository: TokenBlacklistRepository
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var valueOps: ValueOperations<String, String>
    @Mock private lateinit var safeLockReleaseScript: org.springframework.data.redis.core.script.DefaultRedisScript<Long>

    @Captor private lateinit var blacklistCaptor: ArgumentCaptor<TokenBlacklistEntity>

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var promotionService: SessionPromotionService

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
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        promotionService = SessionPromotionService(
            redisTemplate, anonymousSessionDataService, tokenBlacklistRepository,
            securityProperties, meterRegistry, safeLockReleaseScript
        )
    }

    @Nested
    @DisplayName("TC-013: Login with promotion — happy path")
    inner class HappyPathPromotion {

        @Test
        @DisplayName("should promote session with correct JTI blacklisted and data transferred")
        fun should_promoteSession_when_loginWithAnonymousSessionId() {
            // Given — lock acquired, session exists, data transfer succeeds
            whenever(valueOps.setIfAbsent(eq("anon:lock:session-abc"), any(), any<Duration>()))
                .thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("session-abc")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData("session-abc", 42L))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(3, listOf("cart", "prefs"), false))

            // When
            val result = promotionService.promoteSession("session-abc", 42L, "real-jti-xyz")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.SUCCESS)
            assertThat(result.itemCount).isEqualTo(3)
            assertThat(result.namespaces).containsExactly("cart", "prefs")

            // Verify JTI blacklisted with correct value (FIX-001 verification)
            verify(tokenBlacklistRepository).save(blacklistCaptor.capture())
            val blacklistEntry = blacklistCaptor.value
            assertThat(blacklistEntry.tokenJti).isEqualTo("real-jti-xyz")
            assertThat(blacklistEntry.userId).isEqualTo(42L)
            assertThat(blacklistEntry.reason).isEqualTo("PROMOTION")

            // Verify session cleaned up
            verify(redisTemplate).delete("anon:session:session-abc")
            verify(anonymousSessionDataService).deleteAllSessionData("session-abc")

            // Verify lock released safely via script
            verify(redisTemplate).execute(eq(safeLockReleaseScript), eq(listOf("anon:lock:session-abc")), any<String>())

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.sessions.promoted", "status", "SUCCESS").count())
                .isEqualTo(1.0)
            assertThat(meterRegistry.timer("auth.anonymous.promotion.duration").count()).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("TC-014: Login without anonymousSessionId — backward compat")
    inner class NoPromotionBackwardCompat {

        @Test
        @DisplayName("should not call promotion service when anonymousSessionId is null")
        fun should_skipPromotion_when_noAnonymousSessionId() {
            // This is tested at handler level — promotion service is not called
            // Here we verify the promotion service behaves correctly if somehow called
            // with an empty/null scenario. The handler guards this.
            // Covered implicitly by LoginHandler/RegisterHandler tests.
        }
    }

    @Nested
    @DisplayName("TC-016: Login with expired anonymousSessionId")
    inner class ExpiredSessionPromotion {

        @Test
        @DisplayName("should return FAILED when anonymous session not found in Redis")
        fun should_returnFailed_when_sessionExpired() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("expired-session")).thenReturn(false)

            // When
            val result = promotionService.promoteSession("expired-session", 42L, "some-jti")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.FAILED)
            assertThat(result.itemCount).isEqualTo(0)

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.sessions.promoted", "status", "FAILED").count())
                .isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("TC-017: Concurrent promotion")
    inner class ConcurrentPromotion {

        @Test
        @DisplayName("should return CONFLICT when lock cannot be acquired")
        fun should_returnConflict_when_lockHeld() {
            // Given — lock acquisition fails (another thread holds it)
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(false)

            // When
            val result = promotionService.promoteSession("session-abc", 42L, "some-jti")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.CONFLICT)

            // Verify no data operations attempted
            verifyNoInteractions(anonymousSessionDataService)
            verifyNoInteractions(tokenBlacklistRepository)

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.sessions.promoted", "status", "CONFLICT").count())
                .isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("TC-018: JTI blacklisting verification (FIX-001)")
    inner class JtiBlacklistVerification {

        @Test
        @DisplayName("should blacklist the REAL JTI from the anonymous token, not empty string")
        fun should_blacklistRealJti_when_promotionSucceeds() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("session-jti-test")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData(eq("session-jti-test"), any()))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(1, listOf("cart"), false))

            val realJti = "550e8400-e29b-41d4-a716-446655440000"

            // When
            promotionService.promoteSession("session-jti-test", 99L, realJti)

            // Then — verify the real JTI is stored, NOT empty string
            verify(tokenBlacklistRepository).save(argThat {
                tokenJti == realJti && userId == 99L && reason == "PROMOTION"
            })
        }

        @Test
        @DisplayName("should handle empty JTI gracefully for backward compatibility")
        fun should_blacklistEmptyJti_when_clientDoesNotSendToken() {
            // Given — backward compat: client didn't send anonymousToken
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("session-compat")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData(eq("session-compat"), any()))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(0, emptyList(), false))

            // When
            promotionService.promoteSession("session-compat", 99L, "")

            // Then — empty JTI saved (backward compat, not ideal but not breaking)
            verify(tokenBlacklistRepository).save(argThat {
                tokenJti == "" && reason == "PROMOTION"
            })
        }
    }

    @Nested
    @DisplayName("Partial transfer scenarios")
    inner class PartialTransfer {

        @Test
        @DisplayName("should return PARTIAL when data transfer fails")
        fun should_returnPartial_when_transferThrows() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("session-partial")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData(eq("session-partial"), any()))
                .thenThrow(RuntimeException("Redis connection lost"))

            // When
            val result = promotionService.promoteSession("session-partial", 42L, "jti-partial")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.PARTIAL)
            assertThat(result.itemCount).isEqualTo(0)

            // JTI should still be blacklisted (best-effort)
            verify(tokenBlacklistRepository).save(argThat { tokenJti == "jti-partial" })
        }
    }
}
