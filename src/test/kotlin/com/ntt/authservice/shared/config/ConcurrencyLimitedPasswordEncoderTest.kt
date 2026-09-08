package com.ntt.authservice.shared.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for ConcurrencyLimitedPasswordEncoder.
 * Verifies semaphore behavior for encode(), matches(), and upgradeEncoding().
 */
@DisplayName("ConcurrencyLimitedPasswordEncoder")
class ConcurrencyLimitedPasswordEncoderTest {

    private lateinit var delegate: PasswordEncoder
    private lateinit var encoder: ConcurrencyLimitedPasswordEncoder

    @BeforeEach
    fun setUp() {
        delegate = mock(PasswordEncoder::class.java)
        encoder = ConcurrencyLimitedPasswordEncoder(delegate, maxConcurrent = 2)
    }

    @Test
    @DisplayName("encode() delegates to underlying encoder")
    fun `encode delegates correctly`() {
        `when`(delegate.encode("password")).thenReturn("{argon2id}\$hash")

        val result = encoder.encode("password")

        assertEquals("{argon2id}\$hash", result)
        verify(delegate).encode("password")
    }

    @Test
    @DisplayName("matches() delegates to underlying encoder")
    fun `matches delegates correctly`() {
        `when`(delegate.matches("password", "{argon2id}\$hash")).thenReturn(true)

        val result = encoder.matches("password", "{argon2id}\$hash")

        assertTrue(result)
        verify(delegate).matches("password", "{argon2id}\$hash")
    }

    @Test
    @DisplayName("upgradeEncoding() delegates without semaphore")
    fun `upgradeEncoding delegates correctly`() {
        `when`(delegate.upgradeEncoding("{bcrypt}\$hash")).thenReturn(true)

        val result = encoder.upgradeEncoding("{bcrypt}\$hash")

        assertTrue(result)
        verify(delegate).upgradeEncoding("{bcrypt}\$hash")
    }

    @Test
    @DisplayName("FR-010: Concurrent encode() limited to maxConcurrent")
    fun `limits concurrent encode operations`() {
        val maxConcurrent = 2
        val limitedEncoder = ConcurrencyLimitedPasswordEncoder(delegate, maxConcurrent)
        val concurrentCount = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)
        val totalTasks = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(totalTasks)

        `when`(delegate.encode(anyString())).thenAnswer {
            val current = concurrentCount.incrementAndGet()
            maxObserved.updateAndGet { max -> maxOf(max, current) }
            Thread.sleep(50) // Simulate Argon2id work
            concurrentCount.decrementAndGet()
            "{argon2id}\$hash"
        }

        val executor = Executors.newFixedThreadPool(totalTasks)
        repeat(totalTasks) {
            executor.submit {
                startLatch.await()
                limitedEncoder.encode("password$it")
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(maxObserved.get() <= maxConcurrent,
            "Max concurrent operations (${ maxObserved.get() }) should be <= $maxConcurrent")
    }

    @Test
    @DisplayName("encode() releases semaphore on exception")
    fun `encode releases semaphore on exception`() {
        `when`(delegate.encode(anyString())).thenThrow(RuntimeException("Hash failed"))

        assertThrows(RuntimeException::class.java) {
            encoder.encode("password")
        }

        // Should be able to encode again (semaphore released)
        `when`(delegate.encode(anyString())).thenReturn("{argon2id}\$hash")
        val result = encoder.encode("password")
        assertEquals("{argon2id}\$hash", result)
    }

    @Test
    @DisplayName("matches() releases semaphore on exception")
    fun `matches releases semaphore on exception`() {
        `when`(delegate.matches(anyString(), anyString())).thenThrow(RuntimeException("Match failed"))

        assertThrows(RuntimeException::class.java) {
            encoder.matches("password", "{argon2id}\$hash")
        }

        // Should be able to match again (semaphore released)
        `when`(delegate.matches(anyString(), anyString())).thenReturn(true)
        assertTrue(encoder.matches("password", "{argon2id}\$hash"))
    }
}
