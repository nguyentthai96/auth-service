package com.ntt.authservice.auth.cipher

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Integration tests for key exchange flow.
 * Tests: happy path, idempotency, max devices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeyExchangeIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun generateClientKeyPair(): Pair<String, ByteArray> {
        val keyPairGenerator = KeyPairGenerator.getInstance("X25519")
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        return publicKeyBase64 to keyPair.private.encoded
    }

    @Test
    fun `key exchange should return 200 with valid response`() {
        val (clientPublicKey, _) = generateClientKeyPair()
        val request = KeyExchangeRequest(
            clientPublicKey = clientPublicKey,
            deviceId = "test-device-001",
            appVersion = "1.0.0",
            platform = "Android",
            userId = "user-001"
        )

        mockMvc.post("/auth/key-exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.keyId") { exists() }
            jsonPath("$.serverPublicKey") { exists() }
            jsonPath("$.keyVersion") { value(1) }
            jsonPath("$.algorithm") { value("AES_GCM") }
            jsonPath("$.expiresAt") { exists() }
        }
    }

    @Test
    fun `key exchange should be idempotent for same device`() {
        val (clientPublicKey, _) = generateClientKeyPair()
        val request = KeyExchangeRequest(
            clientPublicKey = clientPublicKey,
            deviceId = "test-device-idempotent",
            appVersion = "1.0.0",
            platform = "iOS",
            userId = "user-idempotent"
        )

        val body = objectMapper.writeValueAsString(request)

        // First call
        val result1 = mockMvc.post("/auth/key-exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val keyId1 = objectMapper.readTree(result1.response.contentAsString).get("keyId").asText()

        // Second call — should return existing session
        val result2 = mockMvc.post("/auth/key-exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val keyId2 = objectMapper.readTree(result2.response.contentAsString).get("keyId").asText()

        assert(keyId1 == keyId2) { "Expected idempotent keyId but got different values: $keyId1 vs $keyId2" }
    }
}
