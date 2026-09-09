package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.DeviceResponse
import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Device management API — list active devices, kick device, kick all other devices.
 * Requires authentication (JWT Bearer token).
 *
 * FR-006: List active devices
 * FR-007: Kick specific device
 * FR-008: Kick all other devices
 */
@RestController
@RequestMapping("/auth/devices")
class DeviceController(
    private val loginSessionService: LoginSessionService
) {

    private val log = LoggerFactory.getLogger(DeviceController::class.java)

    /**
     * List all active devices for the current user.
     */
    @GetMapping
    fun listDevices(): ResponseEntity<List<DeviceResponse>> {
        val userId = getCurrentUserId()
        val currentJti = getCurrentJti()
        val sessions = loginSessionService.listDevicesForUser(userId)
        val devices = sessions.map { it.toDeviceResponse(currentJti) }
        return ResponseEntity.ok(devices)
    }

    /**
     * Kick (revoke) a specific device by session ID.
     */
    @DeleteMapping("/{sessionId}")
    fun kickDevice(@PathVariable sessionId: Long): ResponseEntity<Map<String, Any>> {
        val userId = getCurrentUserId()
        val kicked = loginSessionService.kickDevice(sessionId, userId)
        return if (kicked) {
            ResponseEntity.ok(mapOf("message" to "Device kicked successfully" as Any))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Kick all other devices except the current one.
     */
    @DeleteMapping
    fun kickAllOtherDevices(): ResponseEntity<Map<String, Any>> {
        val userId = getCurrentUserId()
        val currentJti = getCurrentJti()
        val kicked = loginSessionService.kickAllOtherDevices(userId, currentJti)
        return ResponseEntity.ok(mapOf(
            "message" to "Other devices kicked successfully" as Any,
            "kickedCount" to kicked as Any
        ))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw InvalidCredentialsException()
    }

    private fun getCurrentJti(): String? {
        val details = SecurityContextHolder.getContext().authentication?.details as? Map<*, *>
        return details?.get("jti") as? String
    }

    private fun LoginSessionEntity.toDeviceResponse(currentJti: String?): DeviceResponse {
        return DeviceResponse(
            sessionId = this.id!!,
            deviceType = this.deviceType,
            browserName = this.browserName,
            osName = this.osName,
            ipAddress = this.ipAddress,
            loginAt = this.loginAt,
            lastActivityAt = this.lastActivityAt,
            deviceName = this.deviceName,
            isCurrent = currentJti != null && this.accessTokenJti == currentJti
        )
    }
}
