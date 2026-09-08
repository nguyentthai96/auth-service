package com.ntt.authservice.shared.config

import com.ntt.authservice.shared.config.SecurityProperties.PasswordProperties
import com.ntt.authservice.shared.config.SecurityProperties.PasswordProperties.Argon2Properties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Unit tests for PasswordEncoderAutoConfiguration.
 * Verifies algorithm selection, backward compatibility, and parameter binding.
 */
@DisplayName("PasswordEncoderAutoConfiguration")
class PasswordEncoderAutoConfigurationTest {

    private fun createConfig(
        algorithm: String = "argon2id",
        bcryptStrength: Int = 12,
        maxConcurrentHashes: Int = 20,
        argon2: Argon2Properties = Argon2Properties()
    ): PasswordEncoderAutoConfiguration {
        val passwordProps = PasswordProperties(
            algorithm = algorithm,
            bcryptStrength = bcryptStrength,
            maxConcurrentHashes = maxConcurrentHashes,
            argon2 = argon2
        )
        val securityProperties = mock(SecurityProperties::class.java)
        `when`(securityProperties.password).thenReturn(passwordProps)
        return PasswordEncoderAutoConfiguration(securityProperties)
    }

    @Nested
    @DisplayName("FR-001: Default Argon2id encoding")
    inner class DefaultArgon2id {

        @Test
        fun `encode produces argon2id prefixed hash`() {
            val encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            val hash = encoder.encode("testPassword123!")!!
            assertTrue(hash.startsWith("{argon2id}"), "Hash should have {argon2id} prefix, got: $hash")
        }

        @Test
        fun `encoded hash can be verified`() {
            val encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            val hash = encoder.encode("testPassword123!")!!
            assertTrue(encoder.matches("testPassword123!", hash))
        }
    }

    @Nested
    @DisplayName("FR-004: Algorithm switch via configuration")
    inner class AlgorithmSwitch {

        @Test
        fun `bcrypt algorithm produces bcrypt prefixed hash`() {
            val encoder = createConfig(algorithm = "bcrypt").passwordEncoder()
            val hash = encoder.encode("testPassword123!")!!
            assertTrue(hash.startsWith("{bcrypt}"), "Hash should have {bcrypt} prefix, got: $hash")
        }

        @Test
        fun `bcrypt encoded hash can be verified`() {
            val encoder = createConfig(algorithm = "bcrypt").passwordEncoder()
            val hash = encoder.encode("testPassword123!")!!
            assertTrue(encoder.matches("testPassword123!", hash))
        }
    }

    @Nested
    @DisplayName("FR-002: Backward compatible with BCrypt hashes")
    inner class BackwardCompatibility {

        @Test
        fun `matches prefixed bcrypt hash when default is argon2id`() {
            val encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            // Encode with bcrypt first, then verify with argon2id-default encoder
            val bcryptEncoder = createConfig(algorithm = "bcrypt").passwordEncoder()
            val bcryptHash = bcryptEncoder.encode("testPassword123!")!!
            assertTrue(encoder.matches("testPassword123!", bcryptHash))
        }

        @Test
        fun `cross-algorithm verification works both ways`() {
            val argon2Encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            val argon2Hash = argon2Encoder.encode("testPassword123!")!!

            val bcryptEncoder = createConfig(algorithm = "bcrypt").passwordEncoder()
            // Argon2id hash should verify with bcrypt-default encoder (via DelegatingPasswordEncoder)
            assertTrue(bcryptEncoder.matches("testPassword123!", argon2Hash))
        }
    }

    @Nested
    @DisplayName("FR-009: Default fallback for bare hashes")
    inner class BareFallback {

        @Test
        fun `matches bare bcrypt hash without prefix`() {
            val encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            // Simulate a pre-migration BCrypt hash (no {bcrypt} prefix)
            val bcrypt = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            val bareHash = bcrypt.encode("testPassword123!")!!

            assertTrue(encoder.matches("testPassword123!", bareHash),
                "Should match bare BCrypt hash via setDefaultPasswordEncoderForMatches fallback")
        }
    }

    @Nested
    @DisplayName("FR-005: Argon2id parameters from YAML")
    inner class Argon2Parameters {

        @Test
        fun `custom argon2 parameters produce valid hash`() {
            val customArgon2 = Argon2Properties(
                saltLength = 16,
                hashLength = 64,
                parallelism = 2,
                memoryCost = 32768,
                iterations = 2
            )
            val encoder = createConfig(argon2 = customArgon2).passwordEncoder()
            val hash = encoder.encode("testPassword123!")!!

            assertTrue(hash.startsWith("{argon2id}"))
            assertTrue(hash.contains("m=32768"), "Hash should contain custom memory cost")
            assertTrue(hash.contains("t=2"), "Hash should contain custom iterations")
            assertTrue(hash.contains("p=2"), "Hash should contain custom parallelism")
        }
    }

    @Nested
    @DisplayName("upgradeEncoding detection")
    inner class UpgradeEncoding {

        @Test
        fun `upgradeEncoding returns true for bcrypt hash when default is argon2id`() {
            val encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            val bcryptEncoder = createConfig(algorithm = "bcrypt").passwordEncoder()
            val bcryptHash = bcryptEncoder.encode("testPassword123!")!!

            assertTrue(encoder.upgradeEncoding(bcryptHash),
                "Should flag BCrypt hash for upgrade when default is Argon2id")
        }

        @Test
        fun `upgradeEncoding returns false for argon2id hash when default is argon2id`() {
            val encoder = createConfig(algorithm = "argon2id").passwordEncoder()
            val hash = encoder.encode("testPassword123!")!!

            assertFalse(encoder.upgradeEncoding(hash),
                "Should NOT flag Argon2id hash for upgrade when default is Argon2id")
        }
    }
}
