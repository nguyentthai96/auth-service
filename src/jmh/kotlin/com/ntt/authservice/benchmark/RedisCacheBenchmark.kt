package com.ntt.authservice.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * JMH microbenchmark for Redis cache serialization comparison.
 *
 * FR-012: Compare Jackson vs JDK serialization for cache payloads.
 * Extends pattern from SerializationBenchmark.kt.
 *
 * Run: ./gradlew jmh --include RedisCacheBenchmark
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class RedisCacheBenchmark {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var session: UserSessionPayload
    private lateinit var jacksonBytes: ByteArray
    private lateinit var jdkBytes: ByteArray

    /**
     * Representative cache payload — mimics a UserSession stored in Redis.
     */
    data class UserSessionPayload(
        val sessionId: String,
        val userId: Long,
        val username: String,
        val roles: List<String>,
        val createdAt: Long,
        val expiresAt: Long,
        val metadata: Map<String, String>,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    @Setup(Level.Trial)
    fun setup() {
        objectMapper = jacksonObjectMapper()

        session = UserSessionPayload(
            sessionId = UUID.randomUUID().toString(),
            userId = 12345L,
            username = "perfuser",
            roles = listOf("USER", "ADMIN"),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000,
            metadata = mapOf(
                "ip" to "192.168.1.100",
                "userAgent" to "Mozilla/5.0 (X11; Linux x86_64)",
                "deviceId" to UUID.randomUUID().toString(),
            ),
        )

        // Pre-serialize for deserialization benchmarks
        jacksonBytes = objectMapper.writeValueAsBytes(session)
        jdkBytes = serializeJdk(session)
    }

    @Benchmark
    fun serializeJackson(bh: Blackhole) {
        val bytes = objectMapper.writeValueAsBytes(session)
        bh.consume(bytes)
    }

    @Benchmark
    fun serializeJdk(bh: Blackhole) {
        val bytes = serializeJdk(session)
        bh.consume(bytes)
    }

    @Benchmark
    fun deserializeJackson(bh: Blackhole) {
        val result = objectMapper.readValue(jacksonBytes, UserSessionPayload::class.java)
        bh.consume(result)
    }

    @Benchmark
    fun deserializeJdk(bh: Blackhole) {
        val result = deserializeJdk(jdkBytes)
        bh.consume(result)
    }

    @Benchmark
    fun roundTripJackson(bh: Blackhole) {
        val bytes = objectMapper.writeValueAsBytes(session)
        val result = objectMapper.readValue(bytes, UserSessionPayload::class.java)
        bh.consume(result)
    }

    @Benchmark
    fun roundTripJdk(bh: Blackhole) {
        val bytes = serializeJdk(session)
        val result = deserializeJdk(bytes)
        bh.consume(result)
    }

    private fun serializeJdk(obj: Serializable): ByteArray {
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(obj) }
        return bos.toByteArray()
    }

    private fun deserializeJdk(bytes: ByteArray): Any {
        val bis = ByteArrayInputStream(bytes)
        return ObjectInputStream(bis).use { it.readObject() }
    }
}
