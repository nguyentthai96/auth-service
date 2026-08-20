package com.ntt.accountservice.device.adapter.`in`.web.dto

import com.ntt.accountservice.device.adapter.out.persistence.entity.UserDeviceEntity
import java.time.Instant

/**
 * Device DTOs — data class + companion from() factory pattern.
 */
data class DeviceResponse(
    val id: Long?,
    val fingerprint: String,
    val deviceName: String?,
    val deviceType: String?,
    val browserName: String?,
    val osName: String?,
    val trusted: Boolean,
    val trustedUntil: Instant?,
    val lastUsedAt: Instant
) {
    companion object {
        fun from(entity: UserDeviceEntity): DeviceResponse {
            return DeviceResponse(
                id = entity.id,
                fingerprint = entity.fingerprint,
                deviceName = entity.deviceName,
                deviceType = entity.deviceType,
                browserName = entity.browserName,
                osName = entity.osName,
                trusted = entity.trusted,
                trustedUntil = entity.trustedUntil,
                lastUsedAt = entity.lastUsedAt
            )
        }
    }
}

data class TrustDeviceRequest(
    val trusted: Boolean = true
)
