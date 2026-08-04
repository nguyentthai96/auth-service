package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakeBaseEntity
import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * System action (READ, CREATE, UPDATE, DELETE, etc.).
 * Lookup table — no audit needed.
 */
@Entity
@Table(name = "actions")
class ActionEntity : SnowflakeBaseEntity() {

    @Column(nullable = false, unique = true, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 100)
    lateinit var name: String

    @Column(length = 255)
    var description: String? = null
}

/**
 * Domain resource (e.g., bookings, rooms, payments).
 */
@Entity
@Table(name = "domain_resources", uniqueConstraints = [UniqueConstraint(columnNames = ["domain_id", "code"])])
class DomainResourceEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(nullable = false, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    var description: String? = null

    @Column(name = "parent_resource_id")
    var parentResourceId: Long? = null
}

/**
 * Permission = Resource × Action. Join table.
 */
@Entity
@Table(name = "permissions", uniqueConstraints = [UniqueConstraint(columnNames = ["resource_id", "action_id"])])
class PermissionEntity : SnowflakeBaseEntity() {

    @Column(name = "resource_id", nullable = false)
    var resourceId: Long = 0

    @Column(name = "action_id", nullable = false)
    var actionId: Long = 0
}

/**
 * Role ↔ Permission grant.
 */
@Entity
@Table(name = "role_permissions", uniqueConstraints = [UniqueConstraint(columnNames = ["role_id", "permission_id"])])
class RolePermissionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "role_id", nullable = false)
    var roleId: Long = 0

    @Column(name = "permission_id", nullable = false)
    var permissionId: Long = 0

    @Column(name = "granted_at", nullable = false)
    var grantedAt: Instant = Instant.now()
}

/**
 * Refresh token store.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity : SnowflakeBaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "token_hash", nullable = false, unique = true)
    lateinit var tokenHash: String

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: Instant

    @Column(nullable = false)
    var revoked: Boolean = false

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
}

/**
 * Blacklisted JWT tokens (revoked before expiry).
 */
@Entity
@Table(name = "token_blacklist")
class TokenBlacklistEntity : SnowflakeBaseEntity() {

    @Column(name = "token_jti", nullable = false, unique = true)
    lateinit var tokenJti: String

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: Instant

    @Column(name = "revoked_at", nullable = false)
    var revokedAt: Instant = Instant.now()

    @Column(length = 100)
    var reason: String? = null
}
