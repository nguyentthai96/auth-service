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
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * JMH microbenchmark for Jackson 3.x serialization/deserialization throughput.
 *
 * FR-005: JMH microbenchmark setup
 *
 * Measures raw JSON serialization performance without Spring context.
 * Results in ops/sec — higher is better.
 *
 * Run: ./gradlew jmh
 * Output: build/results/jmh/results.json
 *
 * Tests serialization of auth-domain-like objects to measure overhead
 * of JSON processing in the auth-service hot path.
 *
 * ⚠️ No Spring context — pure function benchmarking.
 * Uses Jackson 3.x (tools.jackson.databind) matching production config.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class SerializationBenchmark {

    private lateinit var mapper: JsonMapper
    private lateinit var simpleObject: UserDto
    private lateinit var complexObject: AuthResponseDto
    private lateinit var serializedSimple: ByteArray
    private lateinit var serializedComplex: ByteArray

    @Setup(Level.Trial)
    fun setup() {
        mapper = JsonMapper.builder().build()

        simpleObject = UserDto(
            id = 12345L,
            username = "testuser",
            email = "test@example.com",
            role = "USER"
        )

        complexObject = AuthResponseDto(
            accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NSIsImlhdCI6MTY5MjAwMDAwMH0.test-signature",
            refreshToken = "refresh-token-uuid-value-here-12345678",
            tokenType = "Bearer",
            expiresIn = 900,
            user = simpleObject,
            permissions = listOf("READ_PROFILE", "UPDATE_PROFILE", "VIEW_AUDIT_LOG"),
            issuedAt = "2026-08-27T10:00:00Z",
            mfaRequired = false
        )

        serializedSimple = mapper.writeValueAsBytes(simpleObject)
        serializedComplex = mapper.writeValueAsBytes(complexObject)
    }

    // ==================== Serialization (Object → JSON) ====================

    @Benchmark
    fun serializeSimpleObject(bh: Blackhole) {
        val bytes = mapper.writeValueAsBytes(simpleObject)
        bh.consume(bytes)
    }

    @Benchmark
    fun serializeComplexObject(bh: Blackhole) {
        val bytes = mapper.writeValueAsBytes(complexObject)
        bh.consume(bytes)
    }

    @Benchmark
    fun serializeToString(bh: Blackhole) {
        val json = mapper.writeValueAsString(complexObject)
        bh.consume(json)
    }

    // ==================== Deserialization (JSON → Object) ====================

    @Benchmark
    fun deserializeSimpleObject(bh: Blackhole) {
        val obj = mapper.readValue(serializedSimple, UserDto::class.java)
        bh.consume(obj)
    }

    @Benchmark
    fun deserializeComplexObject(bh: Blackhole) {
        val obj = mapper.readValue(serializedComplex, AuthResponseDto::class.java)
        bh.consume(obj)
    }

    // ==================== Round-trip ====================

    @Benchmark
    fun serializeDeserializeRoundTrip(bh: Blackhole) {
        val bytes = mapper.writeValueAsBytes(complexObject)
        val obj = mapper.readValue(bytes, AuthResponseDto::class.java)
        bh.consume(obj)
    }

    // ==================== Benchmark DTOs (simplified auth-domain shapes) ====================

    data class UserDto(
        val id: Long,
        val username: String,
        val email: String,
        val role: String
    )

    data class AuthResponseDto(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val user: UserDto,
        val permissions: List<String>,
        val issuedAt: String,
        val mfaRequired: Boolean
    )
}
