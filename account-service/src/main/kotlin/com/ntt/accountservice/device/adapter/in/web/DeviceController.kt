package com.ntt.accountservice.device.adapter.`in`.web

import com.ntt.accountservice.device.adapter.`in`.web.dto.DeviceResponse
import com.ntt.accountservice.device.adapter.`in`.web.dto.TrustDeviceRequest
import com.ntt.accountservice.device.application.DeviceService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Device controller — REST endpoints for device management (FR-007).
 */
@RestController
@RequestMapping("/api/account/devices")
class DeviceController(
    private val deviceService: DeviceService
) {

    @GetMapping
    fun getDevices(): ResponseEntity<List<DeviceResponse>> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(deviceService.getDevices(userId))
    }

    @PostMapping("/{id}/trust")
    fun trustDevice(@PathVariable id: Long, @RequestBody request: TrustDeviceRequest): ResponseEntity<DeviceResponse> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(deviceService.trustDevice(userId, id, request.trusted))
    }

    @DeleteMapping("/{id}")
    fun removeDevice(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        val userId = getCurrentUserId()
        deviceService.removeDevice(userId, id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw IllegalStateException("User not authenticated")
    }
}
