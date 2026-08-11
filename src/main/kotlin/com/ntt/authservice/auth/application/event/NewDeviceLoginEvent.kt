package com.ntt.authservice.auth.application.event

/**
 * Event emitted when a new, previously unseen device logs in.
 * Can be consumed by notification/alerting systems.
 */
data class NewDeviceLoginEvent(
    val userId: Long,
    val deviceFingerprint: String,
    val ipAddress: String,
    val browserName: String? = null,
    val osName: String? = null
)
