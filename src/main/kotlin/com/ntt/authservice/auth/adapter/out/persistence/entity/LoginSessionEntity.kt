package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

/**
 * Login session entity — tracks active sessions, device info, and login history.
 * Each successful login creates a new record.
 */
@Entity
@Table(name = "login_sessions")
class LoginSessionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "refresh_token_id")
    var refreshTokenId: Long? = null

    @Column(name = "ip_address", nullable = false, length = 45)
    lateinit var ipAddress: String

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null

    @Column(name = "device_fingerprint", length = 64)
    var deviceFingerprint: String? = null

    @Column(name = "device_type", length = 20)
    var deviceType: String? = null

    @Column(name = "browser_name", length = 50)
    var browserName: String? = null

    @Column(name = "os_name", length = 50)
    var osName: String? = null

    @Column(name = "geo_country", length = 3)
    var geoCountry: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_new_device", nullable = false)
    var isNewDevice: Boolean = false

    @Column(name = "login_at", nullable = false)
    lateinit var loginAt: Instant

    @Column(name = "last_activity_at")
    var lastActivityAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "revoke_reason", length = 50)
    var revokeReason: String? = null
}
