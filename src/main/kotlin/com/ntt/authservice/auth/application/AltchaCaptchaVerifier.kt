package com.ntt.authservice.auth.application

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.ntt.authservice.shared.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ALTCHA Proof-of-Work CAPTCHA verifier — self-hosted, zero external dependency.
 * Verifies that the client solved a SHA-256 PoW challenge correctly.
 *
 * Replay protection via Caffeine in-memory cache (salt-based, TTL = challenge TTL).
 */
@Component
class AltchaCaptchaVerifier(
    private val securityProperties: SecurityProperties,
    private val objectMapper: ObjectMapper
) : CaptchaVerifier {

    private val log = LoggerFactory.getLogger(AltchaCaptchaVerifier::class.java)

    // Caffeine cache for replay protection — store used salts
    private val usedSaltsCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(securityProperties.captcha.altcha.challengeTtlSeconds))
        .maximumSize(10_000)
        .build()

    override fun verify(token: String): Boolean {
        return try {
            // Decode Base64 payload
            val json = String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8)
            val payload = objectMapper.readValue(json, AltchaPayload::class.java)

            // 1. Check replay — salt already used?
            if (usedSaltsCache.getIfPresent(payload.salt) != null) {
                log.warn("ALTCHA_REPLAY_DETECTED salt={}", payload.salt)
                return false
            }

            // 2. Verify HMAC signature — HMAC-SHA-256(salt, hmacKey) == signature
            val hmacKey = securityProperties.captcha.altcha.hmacKey
            val expectedSignature = hmacSha256(payload.salt, hmacKey)
            if (expectedSignature != payload.signature) {
                log.warn("ALTCHA_SIGNATURE_MISMATCH salt={}", payload.salt)
                return false
            }

            // 3. Verify PoW solution — SHA-256(salt + number) == challenge
            val computedChallenge = sha256Hex("${payload.salt}${payload.number}")
            if (computedChallenge != payload.challenge) {
                log.warn("ALTCHA_CHALLENGE_MISMATCH salt={}", payload.salt)
                return false
            }

            // 4. Mark salt as used (replay protection)
            usedSaltsCache.put(payload.salt, true)

            log.debug("ALTCHA verified successfully salt={}", payload.salt)
            true
        } catch (ex: Exception) {
            log.error("ALTCHA verification error: {}", ex.message)
            false
        }
    }

    /**
     * Generate a new ALTCHA challenge for the client.
     */
    fun generateChallenge(): AltchaChallenge {
        val salt = java.util.UUID.randomUUID().toString()
        val hmacKey = securityProperties.captcha.altcha.hmacKey
        val difficulty = securityProperties.captcha.altcha.difficulty

        // Pick a random number within difficulty range
        val number = (0 until difficulty).random()

        // challenge = SHA-256(salt + number)
        val challenge = sha256Hex("$salt$number")

        // signature = HMAC-SHA-256(salt, hmacKey)
        val signature = hmacSha256(salt, hmacKey)

        return AltchaChallenge(
            algorithm = "SHA-256",
            challenge = challenge,
            salt = salt,
            signature = signature,
            maxnumber = difficulty
        )
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/**
 * ALTCHA challenge sent to client.
 */
data class AltchaChallenge(
    val algorithm: String,
    val challenge: String,
    val salt: String,
    val signature: String,
    val maxnumber: Int
)

/**
 * ALTCHA solution payload from client (Base64-decoded JSON).
 */
data class AltchaPayload(
    val algorithm: String,
    val challenge: String,
    val number: Long,
    val salt: String,
    val signature: String
)
