package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.shared.web.AuthenticatedController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Session management API — list active sessions, revoke individual or all sessions.
 * Requires authentication (JWT Bearer token).
 *
 * FR-005: Success response i18n — messages resolved via MessageSource.
 */
@RestController
@RequestMapping("/auth/sessions")
class SessionController(
    private val loginSessionService: LoginSessionService
) : AuthenticatedController() {

    /**
     * Get all active sessions for the current user.
     */
    @GetMapping
    fun getActiveSessions(): ResponseEntity<List<SessionResponse>> {
        val sessions = loginSessionService.getActiveSessions(currentUserId())
        return ResponseEntity.ok(sessions.map { it.toResponse() })
    }

    /**
     * Revoke a specific session by ID.
     */
    @DeleteMapping("/{sessionId}")
    fun revokeSession(@PathVariable sessionId: Long): ResponseEntity<Map<String, Any?>> {
        val revoked = loginSessionService.revokeSession(sessionId, currentUserId(), "MANUAL")
        return if (revoked) {
            okMessage("auth.session_revoked")
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Revoke all sessions for the current user (force re-login on all devices).
     */
    @DeleteMapping
    fun revokeAllSessions(): ResponseEntity<Map<String, Any?>> {
        loginSessionService.revokeAllSessions(currentUserId(), "MANUAL_ALL")
        return okMessage("auth.all_sessions_revoked")
    }

    private fun LoginSessionEntity.toResponse(): SessionResponse = SessionResponse(
        id = this.id!!,
        ipAddress = this.ipAddress,
        deviceType = this.deviceType,
        browserName = this.browserName,
        osName = this.osName,
        loginAt = this.loginAt.toString(),
        lastActivityAt = this.lastActivityAt?.toString(),
        isNewDevice = this.isNewDevice
    )
}

/**
 * Session response DTO.
 */
data class SessionResponse(
    val id: Long,
    val ipAddress: String,
    val deviceType: String?,
    val browserName: String?,
    val osName: String?,
    val loginAt: String,
    val lastActivityAt: String?,
    val isNewDevice: Boolean
)
