package com.ntt.authservice.auth.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.shared.config.SecurityProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for CaptchaVerifier interface + AltchaCaptchaVerifier implementation.
 * Tests CAPTCHA verification chain including PoW validation and replay protection.
 *
 * FR-004: CAPTCHA integration
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("CaptchaVerifier Tests")
class CaptchaVerifierTest {

    private lateinit var altchaVerifier: AltchaCaptchaVerifier
    private val objectMapper = ObjectMapper()

    private val testHmacKey = "test-hmac-key-for-captcha-verification-32chars"
    private val testDifficulty = 1000

    @BeforeEach
    fun setUp() {
        val captchaProps = SecurityProperties.CaptchaProperties(
            provider = "altcha",
            altcha = SecurityProperties.CaptchaProperties.AltchaProperties(
                hmacKey = testHmacKey,
                difficulty = testDifficulty,
                challengeTtlSeconds = 300
            )
        )
        val securityProperties = SecurityProperties(captcha = captchaProps)
        altchaVerifier = AltchaCaptchaVerifier(securityProperties, objectMapper)
    }

    /**
     * Helper to create a valid ALTCHA payload token.
     */
    private fun createValidToken(salt: String, number: Long): String {
        val challenge = sha256Hex("$salt$number")
        val signature = hmacSha256(salt, testHmacKey)

        val payload = mapOf(
            "algorithm" to "SHA-256",
            "challenge" to challenge,
            "number" to number,
            "salt" to salt,
            "signature" to signature
        )
        val json = objectMapper.writeValueAsString(payload)
        return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
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

    @Nested
    @DisplayName("AltchaCaptchaVerifier — valid scenarios")
    inner class ValidScenarios {

        @Test
        @DisplayName("TC1: verify(validToken) → true")
        fun shouldReturnTrueForValidToken() {
            // Given
            val salt = "unique-salt-${System.nanoTime()}"
            val number = 42L
            val token = createValidToken(salt, number)

            // When
            val result = altchaVerifier.verify(token)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("TC1b: generateChallenge() and solve it → verify succeeds")
        fun shouldVerifyGeneratedChallenge() {
            // Given — generate a challenge
            val challenge = altchaVerifier.generateChallenge()

            // Solve the challenge (brute-force the number)
            var solvedNumber: Long? = null
            for (n in 0L until challenge.maxnumber.toLong()) {
                val computed = sha256Hex("${challenge.salt}$n")
                if (computed == challenge.challenge) {
                    solvedNumber = n
                    break
                }
            }
            assertNotNull(solvedNumber, "Should find solution within difficulty range")

            // Create solution payload
            val payload = mapOf(
                "algorithm" to challenge.algorithm,
                "challenge" to challenge.challenge,
                "number" to solvedNumber!!,
                "salt" to challenge.salt,
                "signature" to challenge.signature
            )
            val json = objectMapper.writeValueAsString(payload)
            val token = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

            // When
            val result = altchaVerifier.verify(token)

            // Then
            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("AltchaCaptchaVerifier — invalid scenarios")
    inner class InvalidScenarios {

        @Test
        @DisplayName("TC2: verify(invalidToken) → false (wrong number)")
        fun shouldReturnFalseForInvalidToken() {
            // Given — token with wrong number (challenge won't match)
            val salt = "invalid-salt-${System.nanoTime()}"
            val correctNumber = 42L
            val wrongNumber = 99L

            // Create token with WRONG number but challenge computed from correct number
            val challenge = sha256Hex("$salt$correctNumber")
            val signature = hmacSha256(salt, testHmacKey)
            val payload = mapOf(
                "algorithm" to "SHA-256",
                "challenge" to challenge,
                "number" to wrongNumber,  // Wrong number!
                "salt" to salt,
                "signature" to signature
            )
            val json = objectMapper.writeValueAsString(payload)
            val token = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

            // When
            val result = altchaVerifier.verify(token)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("TC3: verify with malformed Base64 → false")
        fun shouldReturnFalseForMalformedBase64() {
            val result = altchaVerifier.verify("not-valid-base64!!!")
            assertFalse(result)
        }

        @Test
        @DisplayName("TC4: verify with empty JSON → false")
        fun shouldReturnFalseForEmptyPayload() {
            val emptyJson = Base64.getEncoder().encodeToString("{}".toByteArray())
            val result = altchaVerifier.verify(emptyJson)
            assertFalse(result)
        }

        @Test
        @DisplayName("TC5: wrong HMAC signature → false")
        fun shouldReturnFalseForWrongSignature() {
            // Given — valid challenge but wrong signature (wrong key)
            val salt = "sig-salt-${System.nanoTime()}"
            val number = 10L
            val challenge = sha256Hex("$salt$number")
            val wrongSignature = hmacSha256(salt, "wrong-hmac-key-entirely")

            val payload = mapOf(
                "algorithm" to "SHA-256",
                "challenge" to challenge,
                "number" to number,
                "salt" to salt,
                "signature" to wrongSignature
            )
            val json = objectMapper.writeValueAsString(payload)
            val token = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

            // When
            val result = altchaVerifier.verify(token)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("TC6: replay attack (same salt reused) → false on second attempt")
        fun shouldRejectReplay() {
            // Given — first verification succeeds
            val salt = "replay-salt-${System.nanoTime()}"
            val number = 7L
            val token = createValidToken(salt, number)

            val firstResult = altchaVerifier.verify(token)
            assertTrue(firstResult, "First verification should succeed")

            // When — same token replayed
            val secondResult = altchaVerifier.verify(token)

            // Then
            assertFalse(secondResult, "Replay should be rejected")
        }
    }

    @Nested
    @DisplayName("NoopCaptchaVerifier")
    inner class NoopTests {

        @Test
        @DisplayName("NoopCaptchaVerifier always returns true")
        fun shouldAlwaysReturnTrue() {
            val noop = NoopCaptchaVerifier()
            assertTrue(noop.verify("anything"))
            assertTrue(noop.verify(""))
            assertTrue(noop.verify("invalid"))
        }
    }
}
