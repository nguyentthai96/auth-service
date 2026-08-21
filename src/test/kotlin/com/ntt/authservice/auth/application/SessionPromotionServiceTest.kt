package com.ntt.authservice.auth.application

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
 * Unit tests for SessionPromotionService:
 * happy path, lock conflict, session expired, partial failure, JTI blacklisting.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessionPromotionService Tests")
class SessionPromotionServiceTest {

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
    @DisplayName("Happy path — full promotion")
    inner class HappyPath {

        @Test
        @DisplayName("should promote session: lock → transfer → blacklist → cleanup → release")
        fun should_promoteSuccessfully_when_allStepsPass() {
            // Given
            whenever(valueOps.setIfAbsent(eq("anon:lock:sess-1"), any(), any<Duration>()))
                .thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-1")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData("sess-1", 42L))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(5, listOf("cart", "prefs"), false))

            // When
            val result = promotionService.promoteSession("sess-1", 42L, "jti-real-uuid")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.SUCCESS)
            assertThat(result.itemCount).isEqualTo(5)
            assertThat(result.namespaces).containsExactly("cart", "prefs")

            // Verify full flow
            verify(anonymousSessionDataService).transferData("sess-1", 42L)
            verify(tokenBlacklistRepository).save(any())
            verify(redisTemplate).delete("anon:session:sess-1")
            verify(anonymousSessionDataService).deleteAllSessionData("sess-1")
            verify(redisTemplate).execute(eq(safeLockReleaseScript), eq(listOf("anon:lock:sess-1")), any<String>())

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.sessions.promoted", "status", "SUCCESS").count())
                .isEqualTo(1.0)
            assertThat(meterRegistry.timer("auth.anonymous.promotion.duration").count()).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Lock conflict")
    inner class LockConflict {

        @Test
        @DisplayName("should return CONFLICT when lock cannot be acquired")
        fun should_returnConflict_when_lockHeldByAnother() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(false)

            // When
            val result = promotionService.promoteSession("sess-1", 42L, "jti-xyz")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.CONFLICT)

            // Verify no data operations
            verifyNoInteractions(anonymousSessionDataService)
            verifyNoInteractions(tokenBlacklistRepository)
        }
    }

    @Nested
    @DisplayName("Session expired")
    inner class SessionExpired {

        @Test
        @DisplayName("should return FAILED when session not found in Redis")
        fun should_returnFailed_when_sessionExpired() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-gone")).thenReturn(false)

            // When
            val result = promotionService.promoteSession("sess-gone", 42L, "jti-123")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.FAILED)

            // Verify lock released
            verify(redisTemplate).execute(eq(safeLockReleaseScript), eq(listOf("anon:lock:sess-gone")), any<String>())
        }
    }

    @Nested
    @DisplayName("Partial failure — transfer fails")
    inner class PartialFailure {

        @Test
        @DisplayName("should return PARTIAL when data transfer throws exception")
        fun should_returnPartial_when_transferFails() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-partial")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData(eq("sess-partial"), any()))
                .thenThrow(RuntimeException("Redis connection lost"))

            // When
            val result = promotionService.promoteSession("sess-partial", 42L, "jti-partial")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.PARTIAL)
            assertThat(result.itemCount).isEqualTo(0)

            // Blacklist should still be attempted (best-effort)
            verify(tokenBlacklistRepository).save(any())
        }

        @Test
        @DisplayName("should return PARTIAL when transfer reports partial success")
        fun should_returnPartial_when_transferReportsPartial() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-2")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData("sess-2", 42L))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(3, listOf("cart"), partial = true))

            // When
            val result = promotionService.promoteSession("sess-2", 42L, "jti-2")

            // Then
            assertThat(result.status).isEqualTo(PromotionResult.Status.PARTIAL)
            assertThat(result.itemCount).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("DB failure on blacklist — best-effort")
    inner class BlacklistFailure {

        @Test
        @DisplayName("should return SUCCESS even when blacklist save fails")
        fun should_returnSuccess_when_blacklistFails() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-3")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData("sess-3", 42L))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(2, listOf("cart"), false))
            whenever(tokenBlacklistRepository.save(any<TokenBlacklistEntity>()))
                .thenThrow(RuntimeException("DB connection failed"))

            // When
            val result = promotionService.promoteSession("sess-3", 42L, "jti-3")

            // Then — SUCCESS because data transfer succeeded (blacklist is best-effort)
            assertThat(result.status).isEqualTo(PromotionResult.Status.SUCCESS)
        }
    }

    @Nested
    @DisplayName("FIX-001: JTI blacklisting verification")
    inner class JtiBlacklist {

        @Test
        @DisplayName("should save REAL JTI to token_blacklist, not empty string")
        fun should_saveRealJti_when_promotionSucceeds() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-jti")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData(eq("sess-jti"), any()))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(1, listOf("cart"), false))

            val realJti = "550e8400-e29b-41d4-a716-446655440000"

            // When
            promotionService.promoteSession("sess-jti", 99L, realJti)

            // Then
            verify(tokenBlacklistRepository).save(blacklistCaptor.capture())
            val entry = blacklistCaptor.value
            assertThat(entry.tokenJti).isEqualTo(realJti)
            assertThat(entry.userId).isEqualTo(99L)
            assertThat(entry.reason).isEqualTo("PROMOTION")
            assertThat(entry.expiresAt).isNotNull()
            assertThat(entry.revokedAt).isNotNull()
        }

        @Test
        @DisplayName("should handle empty JTI for backward compatibility")
        fun should_saveEmptyJti_when_clientDoesNotSendToken() {
            // Given
            whenever(valueOps.setIfAbsent(any<String>(), any(), any<Duration>())).thenReturn(true)
            whenever(anonymousSessionDataService.verifySessionExists("sess-compat")).thenReturn(true)
            whenever(anonymousSessionDataService.transferData(eq("sess-compat"), any()))
                .thenReturn(AnonymousSessionDataService.DataTransferResult(0, emptyList(), false))

            // When
            promotionService.promoteSession("sess-compat", 99L, "")

            // Then — empty JTI still saved (backward compat)
            verify(tokenBlacklistRepository).save(blacklistCaptor.capture())
            assertThat(blacklistCaptor.value.tokenJti).isEmpty()
        }
    }
}
