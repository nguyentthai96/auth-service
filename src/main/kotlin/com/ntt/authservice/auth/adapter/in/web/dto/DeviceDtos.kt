package com.ntt.authservice.auth.adapter.`in`.web.dto

import java.time.Instant

/**
 * Response DTO for device listing.
 */
data class DeviceResponse(
    val sessionId: Long,
    val deviceType: String?,
    val browserName: String?,
    val osName: String?,
    val ipAddress: String,
    val loginAt: Instant,
    val lastActivityAt: Instant?,
    val deviceName: String?,
    val isCurrent: Boolean
)
