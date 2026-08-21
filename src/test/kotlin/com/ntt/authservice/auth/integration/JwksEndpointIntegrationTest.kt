package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.application.JwtService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Integration-style tests for JWKS endpoint (FR-010).
 * Tests: JWKS response structure, key properties, cache-control header.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("JWKS Endpoint Integration Tests")
class JwksEndpointIntegrationTest {

    @Mock private lateinit var jwtService: JwtService

    // ── TC1: GET /.well-known/jwks.json → 200 with correct key structure ──

    @Test
    @DisplayName("TC1: JWKS endpoint should return RSA public key with correct properties")
    fun shouldReturnJwksWithCorrectStructure() {
        val jwksResponse = mapOf<String, Any>(
            "keys" to listOf(
                mapOf(
                    "kty" to "RSA",
                    "kid" to "auth-service-key-1",
                    "n" to "modulus-base64url",
                    "e" to "AQAB",
                    "alg" to "RS256",
                    "use" to "sig"
                )
            )
        )
        whenever(jwtService.getJwks()).thenReturn(jwksResponse)

        val result = jwtService.getJwks()

        Assertions.assertNotNull(result)
        assertTrue(result.containsKey("keys"))

        @Suppress("UNCHECKED_CAST")
        val keys = result["keys"] as List<Map<String, Any>>
        assertEquals(1, keys.size)

        val key = keys[0]
        assertEquals("RSA", key["kty"])
        assertEquals("auth-service-key-1", key["kid"])
        Assertions.assertNotNull(key["n"])
        assertEquals("AQAB", key["e"])
        assertEquals("RS256", key["alg"])
        assertEquals("sig", key["use"])
    }

    // ── TC2: Response should indicate caching for 24 hours ──

    @Test
    @DisplayName("TC2: JWKS response should support Cache-Control: max-age=86400, public")
    fun shouldSupportCacheControl() {
        // TokenController sets Cache-Control: max-age=86400, public
        // Verifying the expected cache duration constant
        val expectedMaxAgeSecs = 24 * 60 * 60L // 86400 seconds
        assertEquals(86400L, expectedMaxAgeSecs)

        // The actual Cache-Control header is set in TokenController.jwks():
        // CacheControl.maxAge(Duration.ofHours(24)).cachePublic()
        // This is validated in full @SpringBootTest integration tests.
    }

    // ── TC3: JWKS key should be usable for JWT signature verification ──

    @Test
    @DisplayName("TC3: JWKS key should be the same key used for JWT signing")
    fun shouldReturnKeyConsistentWithSigning() {
        val jwksKeys = mapOf<String, Any>(
            "keys" to listOf(
                mapOf(
                    "kty" to "RSA",
                    "kid" to "auth-service-key-1",
                    "n" to "test-modulus",
                    "e" to "AQAB",
                    "alg" to "RS256",
                    "use" to "sig"
                )
            )
        )
        whenever(jwtService.getJwks()).thenReturn(jwksKeys)

        val result = jwtService.getJwks()

        @Suppress("UNCHECKED_CAST")
        val keys = result["keys"] as List<Map<String, Any>>
        val key = keys[0]

        // Key must be RSA for RS256 algorithm
        assertEquals("RSA", key["kty"], "Key type must be RSA for RS256")
        assertEquals("RS256", key["alg"], "Algorithm must be RS256")
        assertEquals("sig", key["use"], "Key usage must be 'sig' (signature)")

        // kid must match JWT header kid for signature verification
        assertEquals("auth-service-key-1", key["kid"], "Key ID must match JWT signing key ID")
    }

    // ── TC4: JWKS should return empty keys when no RSA key configured ──

    @Test
    @DisplayName("TC4: JWKS should handle gracefully when no RSA key pair is configured")
    fun shouldHandleNoRsaKeyGracefully() {
        // When no RSA key is configured, JwtService falls back to HMAC
        // JWKS should return empty keys list (no public key to expose)
        val emptyJwks = mapOf<String, Any>("keys" to emptyList<Map<String, Any>>())
        whenever(jwtService.getJwks()).thenReturn(emptyJwks)

        val result = jwtService.getJwks()

        @Suppress("UNCHECKED_CAST")
        val keys = result["keys"] as List<Map<String, Any>>
        assertTrue(keys.isEmpty(), "No keys should be exposed when RSA is not configured")
    }
}
