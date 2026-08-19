package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousRateLimitedException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Unit tests for AnonymousRateLimitService:
 * under limit, at limit, Redis failure (fail-open).
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("AnonymousRateLimitService Tests")
class AnonymousRateLimitServiceTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var rateLimitService: AnonymousRateLimitService

    private val rateLimitConfig = SecurityProperties.MfaProperties.LimitConfig(
        maxAttempts = 5,
        windowSeconds = 3600,
        lockSeconds = 0
    )

    private val anonymousProps = SecurityProperties.AnonymousProperties(
        tokenTtlSeconds = 3600,
        sessionTtlSeconds = 86400,
        maxDataSizeBytes = 65536,
        maxRenewals = 24,
        promotedDataTtlSeconds = 604800,
        rateLimit = rateLimitConfig
    )

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        lenient().whenever(securityProperties.anonymous).thenReturn(anonymousProps)
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        rateLimitService = AnonymousRateLimitService(redisTemplate, securityProperties, meterRegistry)
    }

    @Nested
    @DisplayName("Under rate limit")
    inner class UnderLimit {

        @Test
        @DisplayName("should allow request when under rate limit")
        fun should_allowRequest_when_underLimit() {
            // Given
            whenever(valueOps.increment("anon:rate:192.168.1.1")).thenReturn(1L)

            // When/Then
            assertDoesNotThrow {
                rateLimitService.checkRateLimit("192.168.1.1")
            }
        }

        @Test
        @DisplayName("should set TTL on first request")
        fun should_setTtl_when_firstRequest() {
            // Given
            whenever(valueOps.increment("anon:rate:192.168.1.1")).thenReturn(1L)

            // When
            rateLimitService.checkRateLimit("192.168.1.1")

            // Then
            verify(redisTemplate).expire("anon:rate:192.168.1.1", Duration.ofSeconds(3600))
        }

        @Test
        @DisplayName("should NOT set TTL on subsequent requests")
        fun should_notSetTtl_when_subsequentRequest() {
            // Given
            whenever(valueOps.increment("anon:rate:192.168.1.1")).thenReturn(3L)

            // When
            rateLimitService.checkRateLimit("192.168.1.1")

            // Then
            verify(redisTemplate, never()).expire(any<String>(), any<Duration>())
        }
    }

    @Nested
    @DisplayName("At rate limit")
    inner class AtLimit {

        @Test
        @DisplayName("should throw AnonymousRateLimitedException when limit exceeded")
        fun should_throwRateLimited_when_atLimit() {
            // Given — 6th attempt (limit = 5)
            whenever(valueOps.increment("anon:rate:10.0.0.1")).thenReturn(6L)
            whenever(redisTemplate.getExpire("anon:rate:10.0.0.1")).thenReturn(2400L)

            // When/Then
            val exception = assertThrows<AnonymousRateLimitedException> {
                rateLimitService.checkRateLimit("10.0.0.1")
            }

            assertThat(exception.retryAfterSeconds).isEqualTo(2400L)

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.rate_limited").count()).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("Redis failure — fail-open")
    inner class RedisFailure {

        @Test
        @DisplayName("should allow request through when Redis is unavailable (fail-open)")
        fun should_allowRequest_when_redisDown() {
            // Given
            whenever(valueOps.increment(any())).thenThrow(RedisConnectionFailureException("Connection refused"))

            // When/Then — should NOT throw
            assertDoesNotThrow {
                rateLimitService.checkRateLimit("192.168.1.1")
            }
        }

        @Test
        @DisplayName("should allow request through on unexpected Redis error (fail-open)")
        fun should_allowRequest_when_unexpectedError() {
            // Given
            whenever(valueOps.increment(any())).thenThrow(RuntimeException("Unexpected error"))

            // When/Then — should NOT throw
            assertDoesNotThrow {
                rateLimitService.checkRateLimit("192.168.1.1")
            }
        }
    }
}
