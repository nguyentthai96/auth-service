package com.ntt.authservice.benchmark

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * JMH microbenchmark for AES-GCM encryption/decryption throughput.
 *
 * FR-005: JMH microbenchmark setup
 *
 * Measures raw encryption performance without Spring context overhead.
 * Results in ops/sec — higher is better.
 *
 * Run: ./gradlew jmh
 * Output: build/results/jmh/results.json
 *
 * Benchmark configuration:
 * - Fork: 2 JVM instances (isolate GC, JIT effects)
 * - Warmup: 5 iterations (JIT warmup)
 * - Measurement: 5 iterations (actual measurement)
 * - Mode: Throughput (ops/sec)
 *
 * ⚠️ No Spring context — pure function benchmarking.
 * This measures crypto primitive performance, not full request lifecycle.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class EncryptionBenchmark {

    private lateinit var secretKey: SecretKey
    private lateinit var smallPayload: ByteArray
    private lateinit var mediumPayload: ByteArray
    private lateinit var largePayload: ByteArray
    private lateinit var encryptedSmall: ByteArray
    private lateinit var encryptedSmallIv: ByteArray

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_SIZE = 256
    }

    @Setup(Level.Trial)
    fun setup() {
        // Generate AES-256 key
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE)
        secretKey = keyGen.generateKey()

        // Create test payloads of varying sizes
        smallPayload = """{"userId":12345,"username":"testuser","role":"USER"}""".toByteArray()
        mediumPayload = ByteArray(1024).also { SecureRandom().nextBytes(it) }  // 1 KB
        largePayload = ByteArray(10240).also { SecureRandom().nextBytes(it) }  // 10 KB

        // Pre-encrypt small payload for decryption benchmark
        val (encrypted, iv) = encryptAesGcm(smallPayload)
        encryptedSmall = encrypted
        encryptedSmallIv = iv
    }

    @Benchmark
    fun encryptSmallPayload(bh: Blackhole) {
        val (encrypted, _) = encryptAesGcm(smallPayload)
        bh.consume(encrypted)
    }

    @Benchmark
    fun encryptMediumPayload(bh: Blackhole) {
        val (encrypted, _) = encryptAesGcm(mediumPayload)
        bh.consume(encrypted)
    }

    @Benchmark
    fun encryptLargePayload(bh: Blackhole) {
        val (encrypted, _) = encryptAesGcm(largePayload)
        bh.consume(encrypted)
    }

    @Benchmark
    fun decryptSmallPayload(bh: Blackhole) {
        val decrypted = decryptAesGcm(encryptedSmall, encryptedSmallIv)
        bh.consume(decrypted)
    }

    @Benchmark
    fun encryptDecryptRoundTrip(bh: Blackhole) {
        val (encrypted, iv) = encryptAesGcm(smallPayload)
        val decrypted = decryptAesGcm(encrypted, iv)
        bh.consume(decrypted)
    }

    /**
     * Encrypt data using AES-GCM.
     * @return Pair of (ciphertext, IV)
     */
    private fun encryptAesGcm(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        return Pair(cipher.doFinal(plaintext), iv)
    }

    /**
     * Decrypt data using AES-GCM.
     */
    private fun decryptAesGcm(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(ciphertext)
    }
}
