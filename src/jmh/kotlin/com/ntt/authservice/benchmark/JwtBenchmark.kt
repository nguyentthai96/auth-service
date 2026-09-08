package com.ntt.authservice.benchmark

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
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
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * JMH microbenchmark for JWT RS256 signing and verification.
 *
 * FR-011: JWT is signed/verified on every authenticated request.
 * Measures raw crypto throughput (no Spring context).
 *
 * Run: ./gradlew jmh --include JwtBenchmark
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class JwtBenchmark {

    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey
    private lateinit var preSignedToken: String

    @Setup(Level.Trial)
    fun setup() {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        privateKey = keyPair.private
        publicKey = keyPair.public

        // Pre-sign a token for verification benchmarks
        preSignedToken = signToken()
    }

    @Benchmark
    fun signJwtRS256(bh: Blackhole) {
        val token = signToken()
        bh.consume(token)
    }

    @Benchmark
    fun verifyJwtRS256(bh: Blackhole) {
        val claims = Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(preSignedToken)
        bh.consume(claims)
    }

    @Benchmark
    fun signAndVerifyRoundTrip(bh: Blackhole) {
        val token = signToken()
        val claims = Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
        bh.consume(claims)
    }

    private fun signToken(): String {
        val now = Date()
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject("perfuser")
            .claim("roles", listOf("USER"))
            .issuedAt(now)
            .expiration(Date(now.time + 3600_000))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }
}
