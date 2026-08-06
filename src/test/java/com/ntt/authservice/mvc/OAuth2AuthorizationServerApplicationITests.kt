package com.ntt.authservice.mvc

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/**
 * Integration tests for Auth Service REST endpoints.
 * Tests registration, login, and token refresh flows against H2 in-memory database.
 *
 * @author nguyentthai96
 * @since 2025-03-27
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    /**
     * Test that unauthenticated access to protected endpoints returns 401/403.
     */
    @Test
    fun accessProtectedEndpointWithoutToken_shouldReturn401or403() {
        mockMvc.perform(
            post("/api/permissions/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"domainId":1,"resourceCode":"test","actionCode":"READ"}""")
        )
            .andExpect(status().isForbidden)
    }

    /**
     * Test that login with invalid credentials returns 401.
     */
    @Test
    fun loginWithInvalidCredentials_shouldReturn401() {
        val requestJson = """{"username":"nonexistent-user","password":"wrong-password"}"""

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("AUTH_001"))
    }

    /**
     * Test that registration with missing required fields returns 400.
     */
    @Test
    fun registerWithMissingFields_shouldReturn400() {
        // Blank username and email — will trigger @NotBlank validation
        val invalidJson = """
            {
                "username": "",
                "email": "",
                "password": "short",
                "fullName": "",
                "domainCode": ""
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(status().isBadRequest)
    }

    /**
     * Test that refresh token with invalid token returns error.
     */
    @Test
    fun refreshWithInvalidToken_shouldReturnError() {
        val requestJson = """{"refreshToken": "invalid-token-value"}"""

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isUnauthorized)
    }

    /**
     * Test that auth endpoints are accessible without authentication (permitAll).
     */
    @Test
    fun loginEndpoint_shouldBeAccessibleWithoutAuth() {
        // Login is configured as permitAll in SecurityConfig
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"test","password":"test"}""")
        )
            // Should get 401 (invalid credentials) not 403 (access denied)
            .andExpect(status().isUnauthorized)
    }
}