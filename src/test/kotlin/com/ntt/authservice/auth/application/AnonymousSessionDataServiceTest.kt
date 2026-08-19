package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousDataLimitExceededException
import com.ntt.authservice.shared.exception.AnonymousSessionExpiredException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Unit tests for AnonymousSessionDataService:
 * store, retrieve, delete, transfer data, and size limit enforcement.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("AnonymousSessionDataService Tests")
class AnonymousSessionDataServiceTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var dataService: AnonymousSessionDataService

    private val anonymousProps = SecurityProperties.AnonymousProperties(
        tokenTtlSeconds = 3600,
        sessionTtlSeconds = 86400,
        maxDataSizeBytes = 65536,  // 64KB
        maxRenewals = 24,
        promotedDataTtlSeconds = 604800
    )

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        lenient().whenever(securityProperties.anonymous).thenReturn(anonymousProps)
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        dataService = AnonymousSessionDataService(redisTemplate, securityProperties, meterRegistry)
    }

    @Nested
    @DisplayName("storeData")
    inner class StoreData {

        @Test
        @DisplayName("should store data in Redis with correct key and TTL")
        fun should_storeData_when_validRequest() {
            // Given
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(true)
            whenever(redisTemplate.getExpire("anon:session:sess-1")).thenReturn(3600L)

            // When
            dataService.storeData("sess-1", "cart", "item1", """{"id":1,"qty":2}""")

            // Then
            verify(valueOps).set(
                eq("anon:data:sess-1:cart:item1"),
                eq("""{"id":1,"qty":2}"""),
                eq(Duration.ofSeconds(3600))
            )

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.data.stored").count()).isEqualTo(1.0)
        }

        @Test
        @DisplayName("should throw AnonymousDataLimitExceededException when size exceeded")
        fun should_throwSizeExceeded_when_overLimit() {
            // Given — session exists but data size will exceed limit
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(true)

            // Create a large value that exceeds 64KB
            val largeValue = "x".repeat(70000) // > 64KB

            // Mock getSessionDataSize to return 0 (no existing data), but new data is > max
            // The dataService.getSessionDataSize does SCAN which is complex to mock,
            // so we mock hasKey for the session check

            // When/Then
            assertThrows<AnonymousDataLimitExceededException> {
                dataService.storeData("sess-1", "cart", "item1", largeValue)
            }

            // Verify metrics
            assertThat(meterRegistry.counter("auth.anonymous.data.size_exceeded").count()).isEqualTo(1.0)
        }

        @Test
        @DisplayName("should throw AnonymousSessionExpiredException when session not found")
        fun should_throwExpired_when_sessionNotExists() {
            // Given
            whenever(redisTemplate.hasKey("anon:session:expired-sess")).thenReturn(false)

            // When/Then
            assertThrows<AnonymousSessionExpiredException> {
                dataService.storeData("expired-sess", "cart", "item1", "value")
            }
        }
    }

    @Nested
    @DisplayName("getData")
    inner class GetData {

        @Test
        @DisplayName("should return value when data exists")
        fun should_returnValue_when_dataExists() {
            // Given
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(true)
            whenever(valueOps.get("anon:data:sess-1:cart:item1")).thenReturn("""{"id":1}""")

            // When
            val result = dataService.getData("sess-1", "cart", "item1")

            // Then
            assertThat(result).isEqualTo("""{"id":1}""")
        }

        @Test
        @DisplayName("should return null when data not found")
        fun should_returnNull_when_dataNotExists() {
            // Given
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(true)
            whenever(valueOps.get("anon:data:sess-1:cart:missing")).thenReturn(null)

            // When
            val result = dataService.getData("sess-1", "cart", "missing")

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("deleteData")
    inner class DeleteData {

        @Test
        @DisplayName("should delete data key from Redis")
        fun should_deleteKey_when_called() {
            // Given
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(true)

            // When
            dataService.deleteData("sess-1", "cart", "item1")

            // Then
            verify(redisTemplate).delete("anon:data:sess-1:cart:item1")
        }
    }

    @Nested
    @DisplayName("verifySessionExists")
    inner class VerifySession {

        @Test
        @DisplayName("should return true when session exists")
        fun should_returnTrue_when_sessionExists() {
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(true)
            assertThat(dataService.verifySessionExists("sess-1")).isTrue()
        }

        @Test
        @DisplayName("should return false when session not found")
        fun should_returnFalse_when_sessionNotFound() {
            whenever(redisTemplate.hasKey("anon:session:sess-1")).thenReturn(false)
            assertThat(dataService.verifySessionExists("sess-1")).isFalse()
        }
    }
}
