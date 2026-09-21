package com.ntt.authservice.auth.adapter.`in`.web.dto

/**
 * Typed response DTOs replacing inline mapOf(...) patterns.
 * All controllers should return these DTOs wrapped in ApiResponse<T>
 * instead of ad-hoc Map<String, Any?>.
 */

// ── DeviceController ────────────────────────────────────────────────────
data class KickDeviceResult(val kickedCount: Int)

// ── AccountLifecycleController ──────────────────────────────────────────
data class DeletionRequestResult(
    val requestId: Long,
    val scheduledDeleteAt: String,
    val gracePeriodDays: Long
)

data class DataExportResult(
    val exportId: Long?,
    val status: String,
    val requestedAt: String,
    val expiresAt: String?,
    val fileSizeBytes: Long? = null,
    val downloadUrl: String? = null
)

// ── AdminSessionController ──────────────────────────────────────────────
data class AdminSessionInfo(
    val id: Long?,
    val userId: Long,
    val ipAddress: String?,
    val deviceType: String?,
    val browserName: String?,
    val osName: String?,
    val loginAt: String?,
    val lastActivityAt: String?,
    val isNewDevice: Boolean
)

data class SessionStatsResult(
    val totalActiveSessions: Long,
    val byDeviceType: Map<String, Long>
)

data class RevokeSessionsResult(val revokedCount: Int)

// ── InternalApiController ───────────────────────────────────────────────
data class ServiceTokenResult(
    val token: String,
    val type: String = "Bearer",
    val serviceName: String
)

data class UserRolesResult(
    val userId: Long,
    val domainId: Long,
    val roles: List<Any>,
    val status: String
)

// ── MfaController ───────────────────────────────────────────────────────
data class RecoveryCodesResult(
    val codes: List<String>,
    val count: Int
)

data class RecoveryCodeCountResult(val remainingCodes: Long)

// ── TokenController ─────────────────────────────────────────────────────
data class RevokeResult(val revokedCount: Int, val userId: Long)

// ── SsoController ───────────────────────────────────────────────────────
data class SsoLinkResult(val linked: Boolean)
