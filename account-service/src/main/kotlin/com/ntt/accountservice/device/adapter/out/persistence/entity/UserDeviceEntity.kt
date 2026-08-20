package com.ntt.accountservice.device.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * User device entity — tracks login devices, trust status, fingerprint (FR-007).
 */
@Entity
@Table(
    name = "user_devices",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "fingerprint"])]
)
class UserDeviceEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(nullable = false, length = 255)
    lateinit var fingerprint: String

    @Column(name = "device_name", length = 200)
    var deviceName: String? = null

    @Column(name = "device_type", length = 50)
    var deviceType: String? = null

    @Column(name = "browser_name", length = 100)
    var browserName: String? = null

    @Column(name = "os_name", length = 100)
    var osName: String? = null

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null

    @Column(nullable = false)
    var trusted: Boolean = false

    @Column(name = "trusted_until")
    var trustedUntil: Instant? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Instant = Instant.now()
}
