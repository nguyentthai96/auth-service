package com.ntt.authservice.benchmark

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
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
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import java.util.concurrent.TimeUnit

/**
 * JMH microbenchmark comparing BCrypt vs Argon2id password hashing.
 *
 * Measures:
 * - BCrypt (strength=12): baseline
 * - Argon2id (OWASP 2024 recommended: m=65536 KB, t=3, p=1): new default
 * - Argon2id (balanced: m=65536 KB, t=1, p=1): reduced iterations, max memory
 * - Argon2id (OWASP-A: m=47104 KB, t=1, p=1): OWASP Option A profile
 * - Argon2id (low-memory: m=19456 KB, t=2, p=1): OWASP minimum
 * - DelegatingPasswordEncoder: full production path overhead
 *
 * Run: ./gradlew jmh -Pjmh.includes="Argon2idBenchmark"
 * Output: build/results/jmh/results.json
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput, Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class Argon2idBenchmark {

    // Encoders
    private lateinit var bcryptEncoder: BCryptPasswordEncoder
    private lateinit var argon2Encoder: Argon2PasswordEncoder
    private lateinit var argon2BalancedEncoder: Argon2PasswordEncoder
    private lateinit var argon2OwaspAEncoder: Argon2PasswordEncoder
    private lateinit var argon2LowEncoder: Argon2PasswordEncoder
    private lateinit var delegatingEncoder: DelegatingPasswordEncoder

    // Test data
    private lateinit var rawPassword: String
    private lateinit var bcryptHash: String
    private lateinit var argon2Hash: String
    private lateinit var argon2BalancedHash: String
    private lateinit var argon2OwaspAHash: String
    private lateinit var argon2LowHash: String
    private lateinit var delegatingArgon2Hash: String

    @Setup(Level.Trial)
    fun setup() {
        rawPassword = "BenchmarkPassword123!@#"

        // BCrypt (strength=12) — current production
        bcryptEncoder = BCryptPasswordEncoder(12)
        bcryptHash = bcryptEncoder.encode(rawPassword)!!

        // Argon2id — OWASP 2024 recommended (64MB, 3 iterations, 1 parallelism)
        argon2Encoder = Argon2PasswordEncoder(16, 32, 1, 65536, 3)
        argon2Hash = argon2Encoder.encode(rawPassword)!!

        // Argon2id — Balanced (64MB, 1 iteration, 1 parallelism) — max memory, min iterations
        argon2BalancedEncoder = Argon2PasswordEncoder(16, 32, 1, 65536, 1)
        argon2BalancedHash = argon2BalancedEncoder.encode(rawPassword)!!

        // Argon2id — OWASP Option A (46MB, 1 iteration, 1 parallelism)
        argon2OwaspAEncoder = Argon2PasswordEncoder(16, 32, 1, 47104, 1)
        argon2OwaspAHash = argon2OwaspAEncoder.encode(rawPassword)!!

        // Argon2id — OWASP minimum (19MB, 2 iterations, 1 parallelism)
        argon2LowEncoder = Argon2PasswordEncoder(16, 32, 1, 19456, 2)
        argon2LowHash = argon2LowEncoder.encode(rawPassword)!!

        // DelegatingPasswordEncoder — production path
        val encoders = mapOf(
            "argon2id" to argon2Encoder,
            "bcrypt" to bcryptEncoder
        )
        delegatingEncoder = DelegatingPasswordEncoder("argon2id", encoders)
        delegatingArgon2Hash = delegatingEncoder.encode(rawPassword)!!
    }

    // ===== ENCODE benchmarks =====

    @Benchmark
    fun bcrypt_encode(bh: Blackhole) {
        bh.consume(bcryptEncoder.encode(rawPassword))
    }

    @Benchmark
    fun argon2id_encode(bh: Blackhole) {
        bh.consume(argon2Encoder.encode(rawPassword))
    }

    @Benchmark
    fun argon2id_balanced_encode(bh: Blackhole) {
        bh.consume(argon2BalancedEncoder.encode(rawPassword))
    }

    @Benchmark
    fun argon2id_owaspA_encode(bh: Blackhole) {
        bh.consume(argon2OwaspAEncoder.encode(rawPassword))
    }

    @Benchmark
    fun argon2id_low_encode(bh: Blackhole) {
        bh.consume(argon2LowEncoder.encode(rawPassword))
    }

    @Benchmark
    fun delegating_encode(bh: Blackhole) {
        bh.consume(delegatingEncoder.encode(rawPassword))
    }

    // ===== VERIFY benchmarks =====

    @Benchmark
    fun bcrypt_verify(bh: Blackhole) {
        bh.consume(bcryptEncoder.matches(rawPassword, bcryptHash))
    }

    @Benchmark
    fun argon2id_verify(bh: Blackhole) {
        bh.consume(argon2Encoder.matches(rawPassword, argon2Hash))
    }

    @Benchmark
    fun argon2id_balanced_verify(bh: Blackhole) {
        bh.consume(argon2BalancedEncoder.matches(rawPassword, argon2BalancedHash))
    }

    @Benchmark
    fun argon2id_owaspA_verify(bh: Blackhole) {
        bh.consume(argon2OwaspAEncoder.matches(rawPassword, argon2OwaspAHash))
    }

    @Benchmark
    fun argon2id_low_verify(bh: Blackhole) {
        bh.consume(argon2LowEncoder.matches(rawPassword, argon2LowHash))
    }

    @Benchmark
    fun delegating_verify_argon2(bh: Blackhole) {
        bh.consume(delegatingEncoder.matches(rawPassword, delegatingArgon2Hash))
    }

    @Benchmark
    fun delegating_verify_bcrypt(bh: Blackhole) {
        // Simulates verifying legacy BCrypt hash through DelegatingPasswordEncoder
        bh.consume(delegatingEncoder.matches(rawPassword, "{bcrypt}$bcryptHash"))
    }
}
