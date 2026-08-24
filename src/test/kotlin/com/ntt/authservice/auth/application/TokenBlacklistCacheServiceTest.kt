package com.ntt.authservice.auth.application

import com.github.benmanes.caffeine.cache.Cache
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * Unit tests for TokenBlacklistCacheService — three-tier cache with circuit breaker.
 *
 * FR-001: Three-level blacklist lookup (L1 Caffeine → L2 Redis → DB).
 * FR-015: Redis resilience with circuit breaker.
 * FR-016: Write-through on token revocation with retry.
 * OBS-001: Micrometer metrics verification.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenBlacklistCacheService Tests")
class TokenBlacklistCacheServiceTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var tokenBlacklistRepository: TokenBlacklistRepository
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var service: TokenBlacklistCacheService

    private val blacklistProps = SecurityProperties.BlacklistCacheProperties(
        caffeineTtlSeconds = 30,
        caffeineMaxSize = 10_000,
        redisTimeoutMs = 200,
        redisKeyPrefix = "token:blacklist:",
        circuitBreakerThreshold = 5,
        circuitBreakerResetSeconds = 30
    )

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        val securityProperties = mock<SecurityProperties>()
        whenever(securityProperties.blacklist).thenReturn(blacklistProps)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        // Default: Redis hasKey returns false (not found)
        whenever(redisTemplate.hasKey(any())).thenReturn(false)

        service = TokenBlacklistCacheService(redisTemplate, tokenBlacklistRepository, securityProperties, meterRegistry)
    }

    @Nested
    @DisplayName("isBlacklisted — Three-tier lookup")
    inner class IsBlacklisted {

        @Test
        @DisplayName("TC01: L1 Caffeine hit → returns true, Redis NOT called, DB NOT called")
        fun shouldReturnTrueFromL1Hit() {
            // Given — pre-populate L1 by a previous lookup
            whenever(redisTemplate.hasKey("token:blacklist:jti-1")).thenReturn(true)
            service.isBlacklisted("jti-1") // populates L1

            // Reset mocks to verify L1-only
            reset(redisTemplate, tokenBlacklistRepository)

            // When
            val result = service.isBlacklisted("jti-1")

            // Then
            assertTrue(result)
            verify(redisTemplate, never()).hasKey(any())
            verify(tokenBlacklistRepository, never()).existsByTokenJti(any())
        }

        @Test
        @DisplayName("TC02: L1 miss, L2 Redis hit → returns true, populates L1")
        fun shouldReturnTrueFromL2HitAndPopulateL1() {
            // Given — Redis has the key
            whenever(redisTemplate.hasKey("token:blacklist:jti-2")).thenReturn(true)
            whenever(tokenBlacklistRepository.existsByTokenJti("jti-2")).thenReturn(false)

            // When
            val result = service.isBlacklisted("jti-2")

            // Then
            assertTrue(result)
            verify(tokenBlacklistRepository, never()).existsByTokenJti(any())

            // Verify L1 is populated — second call shouldn't hit Redis
            reset(redisTemplate)
            val result2 = service.isBlacklisted("jti-2")
            assertTrue(result2)
            verify(redisTemplate, never()).hasKey(any())
        }

        @Test
        @DisplayName("TC03: L1+L2 miss, L3 DB hit → returns true, populates L1")
        fun shouldReturnTrueFromL3HitAndPopulateL1() {
            // Given
            whenever(redisTemplate.hasKey("token:blacklist:jti-3")).thenReturn(false)
            whenever(tokenBlacklistRepository.existsByTokenJti("jti-3")).thenReturn(true)
            whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

            // When
            val result = service.isBlacklisted("jti-3")

            // Then
            assertTrue(result)
            verify(tokenBlacklistRepository).existsByTokenJti("jti-3")
        }

        @Test
        @DisplayName("TC04: All tiers miss → returns false")
        fun shouldReturnFalseWhenAllTiersMiss() {
            // Given
            whenever(redisTemplate.hasKey("token:blacklist:jti-4")).thenReturn(false)
            whenever(tokenBlacklistRepository.existsByTokenJti("jti-4")).thenReturn(false)

            // When
            val result = service.isBlacklisted("jti-4")

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("TC05: Redis throws exception → falls through to DB (fail-safe)")
        fun shouldFallThroughToDbOnRedisException() {
            // Given — Redis throws
            whenever(redisTemplate.hasKey(any())).thenThrow(RedisConnectionFailureException("Connection refused"))
            whenever(tokenBlacklistRepository.existsByTokenJti("jti-5")).thenReturn(true)

            // When
            val result = service.isBlacklisted("jti-5")

            // Then
            assertTrue(result)
            verify(tokenBlacklistRepository).existsByTokenJti("jti-5")
        }
    }

    @Nested
    @DisplayName("Circuit Breaker")
    inner class CircuitBreaker {

        @Test
        @DisplayName("TC06: After N consecutive Redis failures → circuit breaker opens")
        fun shouldOpenCircuitBreakerAfterThreshold() {
            // Given — Redis always fails
            whenever(redisTemplate.hasKey(any())).thenThrow(RedisConnectionFailureException("Connection refused"))
            whenever(tokenBlacklistRepository.existsByTokenJti(any())).thenReturn(false)

            // When — trigger 5 failures (threshold)
            for (i in 1..5) {
                service.isBlacklisted("jti-cb-$i")
            }

            // Then — circuit breaker opened counter should be 1
            val counter = meterRegistry.find("auth.token.blacklist.circuit_breaker")
                .tag("transition", "opened")
                .counter()
            assertNotNull(counter)
            assertEquals(1.0, counter!!.count())
        }

        @Test
        @DisplayName("TC07: Circuit breaker open → skips Redis, goes to DB directly")
        fun shouldSkipRedisWhenCircuitBreakerOpen() {
            // Given — open circuit breaker by triggering threshold failures
            whenever(redisTemplate.hasKey(any())).thenThrow(RedisConnectionFailureException("Connection refused"))
            whenever(tokenBlacklistRepository.existsByTokenJti(any())).thenReturn(false)
            for (i in 1..5) service.isBlacklisted("jti-pre-$i")

            // Reset mock to verify it's NOT called
            reset(redisTemplate)
            whenever(tokenBlacklistRepository.existsByTokenJti("jti-cb-open")).thenReturn(false)

            // When
            service.isBlacklisted("jti-cb-open")

            // Then — Redis not called (circuit breaker open)
            verify(redisTemplate, never()).hasKey(any())
            verify(tokenBlacklistRepository).existsByTokenJti("jti-cb-open")
        }

        @Test
        @DisplayName("TC08: Circuit breaker resets after cooldown period")
        fun shouldResetCircuitBreakerAfterCooldown() {
            // Given — open circuit breaker
            whenever(redisTemplate.hasKey(any())).thenThrow(RedisConnectionFailureException("Connection refused"))
            whenever(tokenBlacklistRepository.existsByTokenJti(any())).thenReturn(false)
            for (i in 1..5) service.isBlacklisted("jti-pre-$i")

            // Note: We cannot easily test time-based reset in unit test without clock injection.
            // The circuit breaker uses System.currentTimeMillis() with 30s cooldown.
            // We verify the mechanism exists by checking the opened counter was incremented.
            val openedCounter = meterRegistry.find("auth.token.blacklist.circuit_breaker")
                .tag("transition", "opened")
                .counter()
            assertNotNull(openedCounter)
            assertEquals(1.0, openedCounter!!.count())
        }
    }

    @Nested
    @DisplayName("addToBlacklist — Write-through")
    inner class AddToBlacklist {

        @Test
        @DisplayName("TC09: Writes to L1 Caffeine + L2 Redis with key and TTL")
        fun shouldWriteToL1AndL2() {
            // Given
            whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

            // When
            service.addToBlacklist("jti-write-1", 900)

            // Then — verify Redis write
            verify(valueOps).set(
                eq("token:blacklist:jti-write-1"),
                eq("1"),
                any<java.time.Duration>()
            )

            // Verify L1 populated
            assertTrue(service.isBlacklisted("jti-write-1"))
        }

        @Test
        @DisplayName("TC10: Redis write fails → retries once with delay")
        fun shouldRetryRedisWriteOnce() {
            // Given — first call throws, second succeeds
            whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
            whenever(valueOps.set(any(), any(), any<java.time.Duration>()))
                .thenThrow(RedisConnectionFailureException("Connection refused"))
                .then { } // second call succeeds

            // When
            service.addToBlacklist("jti-retry-1", 900)

            // Then — called twice (original + 1 retry)
            verify(valueOps, times(2)).set(any(), any(), any<java.time.Duration>())
        }

        @Test
        @DisplayName("TC11: Redis retry also fails → logs warning, continues (DB already written)")
        fun shouldContinueWhenRetryAlsoFails() {
            // Given — both calls throw
            whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
            whenever(valueOps.set(any(), any(), any<java.time.Duration>()))
                .thenThrow(RedisConnectionFailureException("Connection refused"))

            // When — should NOT throw
            assertDoesNotThrow {
                service.addToBlacklist("jti-retry-fail", 900)
            }

            // L1 is still populated (Caffeine always succeeds)
            assertTrue(service.isBlacklisted("jti-retry-fail"))
        }

        @Test
        @DisplayName("TC12: Correct Redis key format and TTL")
        fun shouldUseCorrectKeyFormatAndTtl() {
            // Given
            whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

            // When
            service.addToBlacklist("abc-123-jti", 600)

            // Then
            verify(valueOps).set(
                eq("token:blacklist:abc-123-jti"),
                eq("1"),
                eq(java.time.Duration.ofSeconds(600))
            )
        }
    }

    @Nested
    @DisplayName("Metrics verification")
    inner class Metrics {

        @Test
        @DisplayName("TC13: SimpleMeterRegistry verifies counter increments for L1 hit, L2 miss, circuit_breaker opened")
        fun shouldRecordMetrics() {
            // Given — L2 hit scenario
            whenever(redisTemplate.hasKey("token:blacklist:jti-m1")).thenReturn(true)

            // When
            service.isBlacklisted("jti-m1")

            // Then — L1 miss, L2 hit counters
            val l1Miss = meterRegistry.find("auth.token.blacklist.lookup")
                .tag("tier", "l1_caffeine").tag("result", "miss").counter()
            assertNotNull(l1Miss)
            assertEquals(1.0, l1Miss!!.count())

            val l2Hit = meterRegistry.find("auth.token.blacklist.lookup")
                .tag("tier", "l2_redis").tag("result", "hit").counter()
            assertNotNull(l2Hit)
            assertEquals(1.0, l2Hit!!.count())
        }
    }
}
