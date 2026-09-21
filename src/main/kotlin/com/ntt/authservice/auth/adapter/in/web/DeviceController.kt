package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.DeviceResponse
import com.ntt.authservice.auth.adapter.`in`.web.dto.KickDeviceResult
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.shared.web.AuthenticatedController
import com.ntt.basecore.domain.web.payload.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
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
) : AuthenticatedController() {

    private val log = LoggerFactory.getLogger(DeviceController::class.java)

    /**
     * List all active devices for the current user.
     */
    @GetMapping
    fun listDevices(): ResponseEntity<ApiResponse<List<DeviceResponse>>> {
        val sessions = loginSessionService.listDevicesForUser(currentUserId())
        val jti = requestContext.jti
        val devices = sessions.map { it.toDeviceResponse(jti) }
        return okResponse(devices)
    }

    /**
     * Kick (revoke) a specific device by session ID.
     */
    @DeleteMapping("/{sessionId}")
    fun kickDevice(@PathVariable sessionId: Long): ResponseEntity<ApiResponse<Unit>> {
        val kicked = loginSessionService.kickDevice(sessionId, currentUserId())
        return if (kicked) {
            okMessageResponse("device.kicked")
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Kick all other devices except the current one.
     */
    @DeleteMapping
    fun kickAllOtherDevices(): ResponseEntity<ApiResponse<KickDeviceResult>> {
        val jti = requestContext.jti
        val kicked = loginSessionService.kickAllOtherDevices(currentUserId(), jti)
        return okResponse(KickDeviceResult(kickedCount = kicked), "device.kicked_all")
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
