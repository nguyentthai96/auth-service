package com.ntt.authservice.auth.application.event

import java.time.Instant

/**
 * Event emitted when a new, previously unseen device logs in.
 * Consumed by NewDeviceMailHandler for email notification
 * and by notification/alerting systems.
 */
data class NewDeviceLoginEvent(
    val userId: Long,
    val username: String = "",
    val email: String? = null,
    val deviceFingerprint: String,
    val deviceName: String = "",
    val deviceType: String? = null,
    val browserName: String? = null,
    val osName: String? = null,
    val ipAddress: String,
    val loginAt: Instant = Instant.now()
)
