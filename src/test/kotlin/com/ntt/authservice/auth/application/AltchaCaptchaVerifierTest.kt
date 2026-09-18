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
 * Unit tests for AltchaCaptchaVerifier — self-hosted Proof-of-Work CAPTCHA.
 * Covers: challenge generation, PoW verification, HMAC signature, replay protection.
 *
 * FR-014: Altcha PoW CAPTCHA
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("AltchaCaptchaVerifier Tests")
class AltchaCaptchaVerifierTest {

    private lateinit var verifier: AltchaCaptchaVerifier
    private val objectMapper = ObjectMapper()

    private val hmacKey = "test-hmac-secret-key-for-unit-tests"
    private val difficulty = 100 // Low difficulty for fast tests

    @BeforeEach
    fun setUp() {
        val securityProperties = SecurityProperties(
            captcha = SecurityProperties.CaptchaProperties(
                altcha = SecurityProperties.CaptchaProperties.AltchaProperties(
                    hmacKey = hmacKey,
                    difficulty = difficulty,
                    challengeTtlSeconds = 300
                )
            )
        )
        verifier = AltchaCaptchaVerifier(securityProperties, objectMapper)
    }

    @Nested
    @DisplayName("Challenge Generation")
    inner class ChallengeGeneration {

        @Test
        @DisplayName("TC1: Generate challenge → valid SHA-256 format")
        fun shouldGenerateValidChallenge() {
            // When
            val challenge = verifier.generateChallenge()

            // Then
            assertEquals("SHA-256", challenge.algorithm)
            assertNotNull(challenge.challenge)
            assertNotNull(challenge.salt)
            assertNotNull(challenge.signature)
            assertEquals(difficulty, challenge.maxnumber)
            // Challenge hash should be 64 hex chars (SHA-256)
            assertEquals(64, challenge.challenge.length)
            // Signature should be 64 hex chars (HMAC-SHA-256)
            assertEquals(64, challenge.signature.length)
        }

        @Test
        @DisplayName("TC2: Each challenge has unique salt")
        fun shouldGenerateUniqueSalts() {
            // When
            val challenge1 = verifier.generateChallenge()
            val challenge2 = verifier.generateChallenge()

            // Then
            assertNotEquals(challenge1.salt, challenge2.salt)
            assertNotEquals(challenge1.challenge, challenge2.challenge)
        }

        @Test
        @DisplayName("TC3: Challenge signature matches HMAC-SHA-256(salt, hmacKey)")
        fun shouldGenerateCorrectHmacSignature() {
            // When
            val challenge = verifier.generateChallenge()

            // Then — verify HMAC independently
            val expectedSignature = hmacSha256(challenge.salt, hmacKey)
            assertEquals(expectedSignature, challenge.signature)
        }

        @Test
        @DisplayName("TC4: Challenge is solvable within maxnumber iterations")
        fun shouldGenerateSolvableChallenge() {
            // When
            val challenge = verifier.generateChallenge()

            // Then — brute-force solve (should succeed within maxnumber)
            var foundNumber: Long? = null
            for (n in 0 until challenge.maxnumber) {
                val hash = sha256Hex("${challenge.salt}$n")
                if (hash == challenge.challenge) {
                    foundNumber = n.toLong()
                    break
                }
            }
            assertNotNull(foundNumber, "Challenge should be solvable within maxnumber iterations")
        }
    }

    @Nested
    @DisplayName("Verification")
    inner class Verification {

        @Test
        @DisplayName("TC5: Valid PoW solution → verification passes")
        fun shouldVerifyValidSolution() {
            // Given — generate challenge and solve it
            val challenge = verifier.generateChallenge()
            val solution = solveChallenge(challenge)
            assertNotNull(solution, "Test setup: challenge must be solvable")

            val token = buildCaptchaToken(challenge, solution!!)

            // When
            val result = verifier.verify(token)

            // Then
            assertTrue(result, "Valid PoW solution should pass verification")
        }

        @Test
        @DisplayName("TC6: Wrong number → verification fails")
        fun shouldRejectWrongNumber() {
            // Given
            val challenge = verifier.generateChallenge()
            val wrongPayload = AltchaPayload(
                algorithm = challenge.algorithm,
                challenge = challenge.challenge,
                number = 99999, // Wrong number
                salt = challenge.salt,
                signature = challenge.signature
            )
            val token = Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsString(wrongPayload).toByteArray(StandardCharsets.UTF_8)
            )

            // When
            val result = verifier.verify(token)

            // Then
            assertFalse(result, "Wrong number should fail verification")
        }

        @Test
        @DisplayName("TC7: Tampered signature → verification fails")
        fun shouldRejectTamperedSignature() {
            // Given
            val challenge = verifier.generateChallenge()
            val solution = solveChallenge(challenge)!!
            val tamperedPayload = AltchaPayload(
                algorithm = challenge.algorithm,
                challenge = challenge.challenge,
                number = solution,
                salt = challenge.salt,
                signature = "0000000000000000000000000000000000000000000000000000000000000000"
            )
            val token = Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsString(tamperedPayload).toByteArray(StandardCharsets.UTF_8)
            )

            // When
            val result = verifier.verify(token)

            // Then
            assertFalse(result, "Tampered signature should fail verification")
        }

        @Test
        @DisplayName("TC8: Replay attack (same token twice) → second attempt fails")
        fun shouldRejectReplayAttack() {
            // Given — generate and solve
            val challenge = verifier.generateChallenge()
            val solution = solveChallenge(challenge)!!
            val token = buildCaptchaToken(challenge, solution)

            // First verification — should pass
            assertTrue(verifier.verify(token), "First verification should pass")

            // When — replay the same token
            val replayResult = verifier.verify(token)

            // Then
            assertFalse(replayResult, "Replay of used token should fail")
        }

        @Test
        @DisplayName("TC9: Invalid Base64 → verification fails gracefully")
        fun shouldHandleInvalidBase64() {
            // When
            val result = verifier.verify("not-valid-base64!!!")

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("TC10: Empty token → verification fails gracefully")
        fun shouldHandleEmptyToken() {
            // When
            val result = verifier.verify("")

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("TC11: Malformed JSON in Base64 → verification fails gracefully")
        fun shouldHandleMalformedJson() {
            // Given
            val token = Base64.getEncoder().encodeToString("{ invalid json }".toByteArray())

            // When
            val result = verifier.verify(token)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("Full E2E Flow: Generate → Solve → Verify")
    inner class E2EFlow {

        @Test
        @DisplayName("TC12: Complete generate-solve-verify cycle")
        fun shouldCompleteFullCycle() {
            // Step 1: Generate challenge
            val challenge = verifier.generateChallenge()
            assertNotNull(challenge)

            // Step 2: Solve PoW (simulate client)
            val solution = solveChallenge(challenge)
            assertNotNull(solution, "Challenge must be solvable")

            // Step 3: Build token (same as frontend altchaSolver.ts)
            val token = buildCaptchaToken(challenge, solution!!)

            // Step 4: Verify
            assertTrue(verifier.verify(token))
        }

        @Test
        @DisplayName("TC13: Multiple independent challenges do not interfere")
        fun shouldSupportConcurrentChallenges() {
            // Generate two challenges
            val challenge1 = verifier.generateChallenge()
            val challenge2 = verifier.generateChallenge()

            // Solve both
            val solution1 = solveChallenge(challenge1)!!
            val solution2 = solveChallenge(challenge2)!!

            val token1 = buildCaptchaToken(challenge1, solution1)
            val token2 = buildCaptchaToken(challenge2, solution2)

            // Verify in reverse order (2 first, then 1)
            assertTrue(verifier.verify(token2), "Challenge 2 should verify")
            assertTrue(verifier.verify(token1), "Challenge 1 should verify independently")
        }
    }

    // ==================== Helper Methods ====================

    private fun solveChallenge(challenge: AltchaChallenge): Long? {
        for (n in 0 until challenge.maxnumber) {
            val hash = sha256Hex("${challenge.salt}$n")
            if (hash == challenge.challenge) {
                return n.toLong()
            }
        }
        return null
    }

    private fun buildCaptchaToken(challenge: AltchaChallenge, number: Long): String {
        val payload = AltchaPayload(
            algorithm = challenge.algorithm,
            challenge = challenge.challenge,
            number = number,
            salt = challenge.salt,
            signature = challenge.signature
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
}
