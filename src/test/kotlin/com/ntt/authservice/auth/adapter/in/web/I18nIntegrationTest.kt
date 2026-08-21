package com.ntt.authservice.auth.adapter.`in`.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * I18n Integration Test — verifies server-side i18n behavior across representative endpoints.
 *
 * Tests the full request -> locale resolution -> controller -> message rendering -> response cycle.
 * Uses sample-based strategy (D15): representative endpoints covering all i18n code paths,
 * since all 18 action endpoints follow the identical messageSource.getMessage() pattern.
 *
 * Coverage:
 * - FR-001: Error response i18n (ProblemDetail.detail localized)
 * - FR-002: Locale resolution from Accept-Language header
 * - FR-005: Success response i18n (message field localized)
 * - FR-006: Content-Language response header present
 * - FR-010: Fallback chain (missing header -> English)
 * - FR-012: Locale whitelist (unsupported locale -> English)
 * - FR-013: Idempotent resolution (repeated requests = same result)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdGeneratorAspect::class)
@DisplayName("I18n Integration Tests")
class I18nIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    private companion object {
        // Expected messages from auth-messages.properties / auth-messages_vi.properties
        const val EN_REGISTER_SUCCESS = "Registration successful"
        const val VI_REGISTER_SUCCESS = "\u0110\u0103ng k\u00fd th\u00e0nh c\u00f4ng"
        const val EN_INVALID_CREDENTIALS = "Invalid username or password"
        const val VI_INVALID_CREDENTIALS = "Sai t\u00ean \u0111\u0103ng nh\u1eadp ho\u1eb7c m\u1eadt kh\u1ea9u"

        @Volatile
        private var userCounter = 0

        @Synchronized
        fun nextUsername(): String = "i18nuser${++userCounter}_${System.nanoTime()}"
    }

    @BeforeEach
    fun setUp() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM domains WHERE code = 'SYSTEM'", Int::class.java
        )
        if (count == 0) {
            jdbcTemplate.execute(
                """INSERT INTO domains (id, code, name, description, status, config, active, created_at, updated_at)
                   VALUES (1, 'SYSTEM', 'System', 'System Domain', 'ACTIVE', '{}', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
            )
        }
    }

    private fun registerRequestJson(username: String): String = """
        {
            "username": "$username",
            "email": "${username}@example.com",
            "password": "SecurePass123!",
            "fullName": "I18n Test User",
            "domainCode": "SYSTEM"
        }
    """.trimIndent()

    private fun loginRequestJson(): String = """
        {
            "username": "nonexistent_user",
            "password": "wrongpassword"
        }
    """.trimIndent()

    // =========================================================
    // FR-005 + FR-002: Success response with i18n message
    // =========================================================

    @Nested
    @DisplayName("Success Response i18n (FR-005, FR-002)")
    inner class SuccessResponseI18n {

        @Test
        @DisplayName("POST /api/auth/register with Accept-Language: vi returns Vietnamese success message")
        fun registerWithVietnameseLocaleReturnsVietnameseMessage() {
            val username = nextUsername()
            mockMvc.perform(
                post("/api/auth/register")
                    .header("Accept-Language", "vi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequestJson(username))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.message").value(VI_REGISTER_SUCCESS))
                .andExpect(header().string("Content-Language", "vi"))
        }

        @Test
        @DisplayName("POST /api/auth/register with Accept-Language: en returns English success message")
        fun registerWithEnglishLocaleReturnsEnglishMessage() {
            val username = nextUsername()
            mockMvc.perform(
                post("/api/auth/register")
                    .header("Accept-Language", "en")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequestJson(username))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.message").value(EN_REGISTER_SUCCESS))
                .andExpect(header().string("Content-Language", "en"))
        }
    }

    // =========================================================
    // FR-010 + FR-012: Fallback behavior
    // =========================================================

    @Nested
    @DisplayName("Locale Fallback (FR-010, FR-012)")
    inner class LocaleFallback {

        @Test
        @DisplayName("POST /api/auth/register without Accept-Language defaults to English")
        fun registerWithoutAcceptLanguageDefaultsToEnglish() {
            val username = nextUsername()
            mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequestJson(username))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.message").value(EN_REGISTER_SUCCESS))
                .andExpect(header().string("Content-Language", "en"))
        }

        @Test
        @DisplayName("POST /api/auth/register with unsupported locale ja falls back to English")
        fun registerWithUnsupportedLocaleJaFallsBackToEnglish() {
            val username = nextUsername()
            mockMvc.perform(
                post("/api/auth/register")
                    .header("Accept-Language", "ja")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequestJson(username))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.message").value(EN_REGISTER_SUCCESS))
                .andExpect(header().string("Content-Language", "en"))
        }
    }

    // =========================================================
    // FR-001 + FR-004: Error response with i18n ProblemDetail
    // =========================================================

    @Nested
    @DisplayName("Error Response i18n (FR-001, FR-004)")
    inner class ErrorResponseI18n {

        @Test
        @DisplayName("POST /api/auth/login invalid credentials with Accept-Language: vi returns Vietnamese error detail")
        fun invalidLoginWithVietnameseLocaleReturnsVietnameseError() {
            mockMvc.perform(
                post("/api/auth/login")
                    .header("Accept-Language", "vi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequestJson())
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.detail").value(VI_INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.errorCode").value("AUTH_001"))
                .andExpect(header().string("Content-Language", "vi"))
        }

        @Test
        @DisplayName("POST /api/auth/login invalid credentials without Accept-Language returns English error detail")
        fun invalidLoginWithoutAcceptLanguageReturnsEnglishError() {
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequestJson())
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.detail").value(EN_INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.errorCode").value("AUTH_001"))
                .andExpect(header().string("Content-Language", "en"))
        }

        @Test
        @DisplayName("POST /api/auth/login with unsupported locale fr falls back to English error")
        fun loginErrorWithUnsupportedLocaleFrFallsBackToEnglish() {
            mockMvc.perform(
                post("/api/auth/login")
                    .header("Accept-Language", "fr")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequestJson())
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.detail").value(EN_INVALID_CREDENTIALS))
                .andExpect(header().string("Content-Language", "en"))
        }
    }

    // =========================================================
    // FR-006: Content-Language header present on all responses
    // =========================================================

    @Nested
    @DisplayName("Content-Language Header (FR-006)")
    inner class ContentLanguageHeader {

        @Test
        @DisplayName("Content-Language header is present on success response")
        fun contentLanguageHeaderPresentOnSuccess() {
            val username = nextUsername()
            mockMvc.perform(
                post("/api/auth/register")
                    .header("Accept-Language", "vi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequestJson(username))
            )
                .andExpect(header().exists("Content-Language"))
        }

        @Test
        @DisplayName("Content-Language header is present on error response")
        fun contentLanguageHeaderPresentOnError() {
            mockMvc.perform(
                post("/api/auth/login")
                    .header("Accept-Language", "en")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequestJson())
            )
                .andExpect(header().exists("Content-Language"))
        }
    }

    // =========================================================
    // FR-013: Idempotent locale resolution
    // =========================================================

    @Nested
    @DisplayName("Idempotent Resolution (FR-013)")
    inner class IdempotentResolution {

        @Test
        @DisplayName("Same Accept-Language header always returns same Content-Language (idempotent)")
        fun sameAcceptLanguageAlwaysReturnsSameContentLanguage() {
            // Use login with invalid creds (returns 401 but resolves locale) to avoid
            // creating multiple users. Error responses also go through locale resolution.
            repeat(3) {
                mockMvc.perform(
                    post("/api/auth/login")
                        .header("Accept-Language", "vi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson())
                )
                    .andExpect(header().string("Content-Language", "vi"))
                    .andExpect(jsonPath("$.detail").value(VI_INVALID_CREDENTIALS))
            }
        }
    }
}
