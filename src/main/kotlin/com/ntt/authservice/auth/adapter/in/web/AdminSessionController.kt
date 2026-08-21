package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.auth.application.LoginSessionService
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Admin Session Management API (FR-008).
 *
 * Provides System Administrator endpoints to:
 * - List all active sessions (paginated)
 * - View session statistics (total, by device type)
 * - Force revoke sessions for a specific user
 *
 * Requires ADMIN/SUPER_ADMIN role (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/sessions")
class AdminSessionController(
    private val loginSessionService: LoginSessionService,
    private val loginSessionRepository: LoginSessionRepository,
    private val messageSource: MessageSource
) {

    /**
     * List all active sessions (paginated).
     */
    @GetMapping
    fun listAllActiveSessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any?>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "loginAt"))
        val sessionPage = loginSessionRepository.findBySessionActiveTrue(pageable)

        val sessions = sessionPage.content.map { session ->
            mapOf(
                "id" to session.id,
                "userId" to session.userId,
                "ipAddress" to session.ipAddress,
                "deviceType" to session.deviceType,
                "browserName" to session.browserName,
                "osName" to session.osName,
                "loginAt" to session.loginAt?.toString(),
                "lastActivityAt" to session.lastActivityAt?.toString(),
                "isNewDevice" to session.isNewDevice
            )
        }

        return ResponseEntity.ok(mapOf(
            "content" to sessions,
            "page" to page,
            "size" to size,
            "totalElements" to sessionPage.totalElements,
            "totalPages" to sessionPage.totalPages
        ))
    }

    /**
     * Get session statistics — total active, breakdown by device type.
     */
    @GetMapping("/stats")
    fun getSessionStats(): ResponseEntity<Map<String, Any?>> {
        val totalActive = loginSessionRepository.countBySessionActiveTrue()
        val deviceBreakdown = loginSessionRepository.countActiveSessionsByDeviceType()
            .associate { arr -> (arr[0] as? String ?: "UNKNOWN") to (arr[1] as Long) }

        return ResponseEntity.ok(mapOf(
            "totalActiveSessions" to totalActive,
            "byDeviceType" to deviceBreakdown
        ))
    }

    /**
     * Force revoke all sessions for a specific user.
     */
    @DeleteMapping("/user/{userId}")
    fun forceRevokeUserSessions(@PathVariable userId: Long): ResponseEntity<Map<String, Any?>> {
        val sessions = loginSessionService.getActiveSessions(userId)
        loginSessionService.revokeAllSessions(userId, "ADMIN_FORCE_REVOKE")
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.admin_sessions_revoked", arrayOf(userId), "All sessions revoked for user $userId", locale)
        return ResponseEntity.ok(mapOf(
            "message" to message,
            "revokedCount" to sessions.size
        ))
    }
}
