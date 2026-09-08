package com.ntt.authservice.auth.integration

import com.ntt.authservice.shared.config.PasswordEncoderAutoConfiguration
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.config.SecurityProperties.PasswordProperties
import com.ntt.authservice.shared.config.SecurityProperties.PasswordProperties.Argon2Properties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Integration-level tests for the login rehash flow.
 * Tests the full DelegatingPasswordEncoder chain (encode → verify → upgradeEncoding → re-encode)
 * without Spring context to keep tests fast and deterministic.
 *
 * FR-003: Rehash-on-login transparent
 * FR-006: Password history cross-algorithm
 */
@DisplayName("Login Rehash Integration")
class LoginRehashIntegrationTest {

    private fun createEncoder(algorithm: String = "argon2id"): org.springframework.security.crypto.password.PasswordEncoder {
        val passwordProps = PasswordProperties(
            algorithm = algorithm,
            bcryptStrength = 10, // Lower strength for faster tests
            argon2 = Argon2Properties(
                memoryCost = 4096, // Lower memory for faster tests
                iterations = 1
            )
        )
        val securityProperties = mock(SecurityProperties::class.java)
        `when`(securityProperties.password).thenReturn(passwordProps)
        return PasswordEncoderAutoConfiguration(securityProperties).passwordEncoder()
    }

    @Nested
    @DisplayName("FR-003: BCrypt → Argon2id rehash flow")
    inner class RehashFlow {

        @Test
        fun `bcrypt hash detected as needing upgrade when default is argon2id`() {
            val encoder = createEncoder("argon2id")
            val bcryptEncoder = createEncoder("bcrypt")

            val bcryptHash = bcryptEncoder.encode("password123")!!
            assertTrue(bcryptHash.startsWith("{bcrypt}"))

            // DelegatingPasswordEncoder.upgradeEncoding detects non-default algorithm
            assertTrue(encoder.upgradeEncoding(bcryptHash),
                "BCrypt hash should be flagged for upgrade")
        }

        @Test
        fun `argon2id hash NOT flagged for upgrade when default is argon2id`() {
            val encoder = createEncoder("argon2id")

            val argon2Hash = encoder.encode("password123")!!
            assertTrue(argon2Hash.startsWith("{argon2id}"))

            assertFalse(encoder.upgradeEncoding(argon2Hash),
                "Argon2id hash should NOT be flagged for upgrade")
        }

        @Test
        fun `full rehash cycle - bcrypt encode then argon2id re-encode`() {
            val encoder = createEncoder("argon2id")
            val bcryptEncoder = createEncoder("bcrypt")

            // Step 1: Original BCrypt hash
            val originalHash = bcryptEncoder.encode("myPassword!")!!
            assertTrue(encoder.matches("myPassword!", originalHash))
            assertTrue(encoder.upgradeEncoding(originalHash))

            // Step 2: Re-encode with default (Argon2id)
            val newHash = encoder.encode("myPassword!")!!
            assertTrue(newHash.startsWith("{argon2id}"))
            assertTrue(encoder.matches("myPassword!", newHash))
            assertFalse(encoder.upgradeEncoding(newHash))
        }
    }

    @Nested
    @DisplayName("FR-006: Cross-algorithm password history")
    inner class CrossAlgorithm {

        @Test
        fun `encoder verifies both bcrypt and argon2id hashes`() {
            val encoder = createEncoder("argon2id")
            val bcryptEncoder = createEncoder("bcrypt")

            val password = "historyCheck123!"
            val bcryptHash = bcryptEncoder.encode(password)!!
            val argon2Hash = encoder.encode(password)!!

            // Simulate password history check — both old (bcrypt) and new (argon2id) hashes verify
            assertTrue(encoder.matches(password, bcryptHash), "BCrypt hash should verify")
            assertTrue(encoder.matches(password, argon2Hash), "Argon2id hash should verify")
        }

        @Test
        fun `wrong password fails for both algorithms`() {
            val encoder = createEncoder("argon2id")
            val bcryptEncoder = createEncoder("bcrypt")

            val bcryptHash = bcryptEncoder.encode("correctPassword")!!
            val argon2Hash = encoder.encode("correctPassword")!!

            assertFalse(encoder.matches("wrongPassword", bcryptHash))
            assertFalse(encoder.matches("wrongPassword", argon2Hash))
        }
    }

    @Nested
    @DisplayName("Rollback scenario: switch back to bcrypt")
    inner class RollbackScenario {

        @Test
        fun `argon2id hashes still verify after switching default to bcrypt`() {
            val argon2Encoder = createEncoder("argon2id")
            val argon2Hash = argon2Encoder.encode("password123")!!

            // Rollback: switch default to bcrypt
            val bcryptEncoder = createEncoder("bcrypt")
            assertTrue(bcryptEncoder.matches("password123", argon2Hash),
                "Argon2id hash should still verify after rollback to bcrypt default")
        }
    }
}
