package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * User account entity.
 * Inherits: id (Snowflake), createdAt, updatedAt, createdBy, updatedBy (Instant), active (soft-delete)
 */
@Entity
@Table(name = "users")
class UserEntity : SnowflakePersistentAuditableEntity() {

    @Column(nullable = false, unique = true, length = 100)
    lateinit var username: String

    @Column(nullable = false, unique = true, length = 255)
    lateinit var email: String

    @Column(name = "password_hash", nullable = false, length = 255)
    lateinit var passwordHash: String

    @Column(name = "full_name", nullable = false, length = 200)
    lateinit var fullName: String

    @Column(length = 20)
    var phone: String? = null

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"

    @Column(name = "failed_login_count", nullable = false)
    var failedLoginCount: Int = 0

    @Column(name = "locked_until_at")
    var lockedUntilAt: java.time.Instant? = null

    @Version
    var version: Int = 0
}
