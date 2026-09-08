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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.concurrent.TimeUnit

/**
 * JMH microbenchmark for BCrypt password hashing.
 *
 * FR-010: BCrypt is the primary cost center for login endpoints.
 * Measures hash + verify throughput at strength 12 (production default).
 *
 * Run: ./gradlew jmh --include BCryptBenchmark
 * Output: build/results/jmh/results.json
 *
 * Expected results:
 * - hashPassword: ~3-5 ops/sec (BCrypt strength 12 is intentionally slow)
 * - verifyPassword: ~3-5 ops/sec
 * - This is the theoretical max login TPS per thread (crypto-bound)
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
open class BCryptBenchmark {

    private lateinit var encoder: BCryptPasswordEncoder
    private lateinit var rawPassword: String
    private lateinit var hashedPassword: String

    @Setup(Level.Trial)
    fun setup() {
        encoder = BCryptPasswordEncoder(12)
        rawPassword = "PerfTestPassword123!"
        hashedPassword = encoder.encode(rawPassword)!!
    }

    @Benchmark
    fun hashPassword(bh: Blackhole) {
        val hash = encoder.encode(rawPassword)
        bh.consume(hash)
    }

    @Benchmark
    fun verifyPassword(bh: Blackhole) {
        val matches = encoder.matches(rawPassword, hashedPassword)
        bh.consume(matches)
    }

    @Benchmark
    fun hashAndVerifyRoundTrip(bh: Blackhole) {
        val hash = encoder.encode(rawPassword)
        val matches = encoder.matches(rawPassword, hash)
        bh.consume(matches)
    }
}
