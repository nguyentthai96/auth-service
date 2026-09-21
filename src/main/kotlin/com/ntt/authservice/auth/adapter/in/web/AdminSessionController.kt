package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.AdminSessionInfo
import com.ntt.authservice.auth.adapter.`in`.web.dto.RevokeSessionsResult
import com.ntt.authservice.auth.adapter.`in`.web.dto.SessionStatsResult
import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.shared.web.AdminController
import com.ntt.basecore.domain.web.payload.ApiResponse
import com.ntt.basecore.domain.web.payload.PageResponse
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
@RequestMapping("/admin/sessions")
class AdminSessionController(
    private val loginSessionService: LoginSessionService,
    private val loginSessionRepository: LoginSessionRepository
) : AdminController() {

    /**
     * List all active sessions (paginated).
     */
    @GetMapping
    fun listAllActiveSessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PageResponse<AdminSessionInfo>>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "loginAt"))
        val sessionPage = loginSessionRepository.findBySessionActiveTrue(pageable)
            .map { session ->
                AdminSessionInfo(
                    id = session.id,
                    userId = session.userId,
                    ipAddress = session.ipAddress,
                    deviceType = session.deviceType,
                    browserName = session.browserName,
                    osName = session.osName,
                    loginAt = session.loginAt?.toString(),
                    lastActivityAt = session.lastActivityAt?.toString(),
                    isNewDevice = session.isNewDevice
                )
            }

        return pagedOkResponse(sessionPage)
    }

    /**
     * Get session statistics — total active, breakdown by device type.
     */
    @GetMapping("/stats")
    fun getSessionStats(): ResponseEntity<ApiResponse<SessionStatsResult>> {
        val totalActive = loginSessionRepository.countBySessionActiveTrue()
        val deviceBreakdown = loginSessionRepository.countActiveSessionsByDeviceType()
            .associate { arr -> (arr[0] as? String ?: "UNKNOWN") to (arr[1] as Long) }

        return okResponse(
            SessionStatsResult(
                totalActiveSessions = totalActive,
                byDeviceType = deviceBreakdown
            )
        )
    }

    /**
     * Force revoke all sessions for a specific user.
     */
    @DeleteMapping("/user/{userId}")
    fun forceRevokeUserSessions(@PathVariable userId: Long): ResponseEntity<ApiResponse<RevokeSessionsResult>> {
        val sessions = loginSessionService.getActiveSessions(userId)
        loginSessionService.revokeAllSessions(userId, "ADMIN_FORCE_REVOKE")
        return okResponse(
            RevokeSessionsResult(revokedCount = sessions.size),
            "auth.admin_sessions_revoked", userId
        )
    }
}

