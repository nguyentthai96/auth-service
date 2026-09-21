package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.auth.application.LoginSessionService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.MessageSource
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.Locale

/**
 * Integration tests for AdminSessionController — admin force logout endpoints.
 * Tests authentication, authorization, and response structure.
 *
 * FR-012: Force logout / session revocation
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AdminSessionController Integration Tests")
class AdminSessionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var loginSessionService: LoginSessionService

    @MockitoBean
    private lateinit var loginSessionRepository: LoginSessionRepository

    @Test
    @DisplayName("TC1: DELETE /api/admin/sessions/user/{userId} with ADMIN role → 200 + revokedCount")
    @WithMockUser(roles = ["ADMIN"])
    fun shouldRevokeSessionsWithAdminRole() {
        // Given
        whenever(loginSessionService.getActiveSessions(42L)).thenReturn(listOf(mock(), mock(), mock()))

        // When/Then
        mockMvc.perform(delete("/admin/sessions/user/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.revokedCount").value(3))
            .andExpect(jsonPath("$.message").exists())

        verify(loginSessionService).revokeAllSessions(42L, "ADMIN_FORCE_REVOKE")
    }

    @Test
    @DisplayName("TC2: DELETE without ADMIN role → 403")
    @WithMockUser(roles = ["USER"])
    fun shouldReject403WithoutAdminRole() {
        mockMvc.perform(delete("/admin/sessions/user/42"))
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("TC3: DELETE without auth → 401")
    fun shouldReject401WithoutAuth() {
        mockMvc.perform(delete("/admin/sessions/user/42"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("TC4: GET /api/admin/sessions → list active sessions (paginated)")
    @WithMockUser(roles = ["ADMIN"])
    fun shouldListActiveSessions() {
        // Given — return empty page
        whenever(loginSessionRepository.findBySessionActiveTrue(any<Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        // When/Then
        mockMvc.perform(get("/admin/sessions")
            .param("page", "0")
            .param("size", "20"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isArray)
            .andExpect(jsonPath("$.data.totalElements").value(0))
    }

    @Test
    @DisplayName("TC5: GET /api/admin/sessions/stats with ADMIN role → session stats")
    @WithMockUser(roles = ["ADMIN"])
    fun shouldReturnSessionStats() {
        // Given
        whenever(loginSessionRepository.countBySessionActiveTrue()).thenReturn(15L)
        whenever(loginSessionRepository.countActiveSessionsByDeviceType())
            .thenReturn(listOf(arrayOf("DESKTOP", 10L), arrayOf("MOBILE", 5L)))

        // When/Then
        mockMvc.perform(get("/admin/sessions/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalActiveSessions").value(15))
            .andExpect(jsonPath("$.data.byDeviceType").exists())
    }
}
