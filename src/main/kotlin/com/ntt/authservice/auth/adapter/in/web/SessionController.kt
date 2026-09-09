package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
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
    private val loginSessionService: LoginSessionService,
    private val messageSource: MessageSource
) {

    /**
     * Get all active sessions for the current user.
     */
    @GetMapping
    fun getActiveSessions(): ResponseEntity<List<SessionResponse>> {
        val userId = getCurrentUserId()
        val sessions = loginSessionService.getActiveSessions(userId)
        return ResponseEntity.ok(sessions.map { it.toResponse() })
    }

    /**
     * Revoke a specific session by ID.
     */
    @DeleteMapping("/{sessionId}")
    fun revokeSession(@PathVariable sessionId: Long): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        val revoked = loginSessionService.revokeSession(sessionId, userId, "MANUAL")
        return if (revoked) {
            val locale = LocaleContextHolder.getLocale()
            val message = messageSource.getMessage("auth.session_revoked", null, "Session revoked", locale)
            ResponseEntity.ok(mapOf("message" to message))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Revoke all sessions for the current user (force re-login on all devices).
     */
    @DeleteMapping
    fun revokeAllSessions(): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        loginSessionService.revokeAllSessions(userId, "MANUAL_ALL")
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.all_sessions_revoked", null, "All sessions revoked", locale)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw InvalidCredentialsException()
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
