package com.ntt.authservice.auth.adapter.`in`.web

import org.junit.jupiter.api.DisplayName
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
import org.junit.jupiter.api.BeforeEach
import org.springframework.context.annotation.Import


/**
 * Integration test for auth endpoints using H2 in-memory DB.
 * Tests full request → controller → handler → port → DB → response cycle.
 *
 * TODO: Migrate to Testcontainers with PostgreSQL for full parity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdGeneratorAspect::class)
@DisplayName("Auth API Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @BeforeEach
    fun setUp() {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domains WHERE code = 'SYSTEM'", Int::class.java)
        if (count == 0) {
            jdbcTemplate.execute("INSERT INTO domains (id, code, name, description, status, config, active, created_at, updated_at) VALUES (1, 'SYSTEM', 'System', 'System Domain', 'ACTIVE', '{}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
        }
    }

    @Test
    @DisplayName("POST /api/auth/register should create user and return tokens")
    fun shouldRegisterUser() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "newuser",
                        "email": "newuser@example.com",
                        "password": "SecurePass123!",
                        "fullName": "New User",
                        "domainCode": "SYSTEM"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.username").value("newuser"))
    }

    @Test
    @DisplayName("POST /api/auth/login should fail with invalid credentials")
    fun shouldRejectInvalidLogin() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "nonexistent",
                        "password": "wrongpassword"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("POST /api/auth/register should reject duplicate username")
    fun shouldRejectDuplicateUsername() {
        // First registration
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "duplicateuser",
                        "email": "dup1@example.com",
                        "password": "SecurePass123!",
                        "fullName": "Dup User",
                        "domainCode": "SYSTEM"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)

        // Second registration — same username
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "duplicateuser",
                        "email": "dup2@example.com",
                        "password": "SecurePass123!",
                        "fullName": "Dup User 2",
                        "domainCode": "SYSTEM"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
    }
}
