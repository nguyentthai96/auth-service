package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Business domain entity (e.g., booking, rental, loyalty).
 * Inherits: id, audit fields, active from base-core.
 */
@Entity
@Table(name = "domains")
class DomainEntity : SnowflakePersistentAuditableEntity() {

    @Column(nullable = false, unique = true, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    var description: String? = null

    @Column(columnDefinition = "jsonb")
    var config: String = "{}"

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"
}

/**
 * User ↔ Domain membership.
 */
@Entity
@Table(name = "user_domains", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "domain_id"])])
class UserDomainEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false

    @Column(name = "joined_at", nullable = false)
    var joinedAt: java.time.Instant = java.time.Instant.now()
}

/**
 * Group within a domain.
 */
@Entity
@Table(name = "groups", uniqueConstraints = [UniqueConstraint(columnNames = ["domain_id", "name"])])
class GroupEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(nullable = false, length = 200)
    lateinit var name: String

    var description: String? = null
}

/**
 * User ↔ Group membership.
 */
@Entity
@Table(name = "user_groups", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "group_id"])])
class UserGroupEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "group_id", nullable = false)
    var groupId: Long = 0

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: java.time.Instant = java.time.Instant.now()
}

/**
 * Role within a domain (e.g., DOMAIN_ADMIN, RECEPTIONIST, VIEWER).
 */
@Entity
@Table(name = "domain_roles", uniqueConstraints = [UniqueConstraint(columnNames = ["domain_id", "code"])])
class DomainRoleEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(nullable = false, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    var description: String? = null

    @Column(name = "hierarchy_level", nullable = false)
    var hierarchyLevel: Int = 99

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false
}

/**
 * Group ↔ Role assignment.
 */
@Entity
@Table(name = "group_roles", uniqueConstraints = [UniqueConstraint(columnNames = ["group_id", "role_id"])])
class GroupRoleEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "group_id", nullable = false)
    var groupId: Long = 0

    @Column(name = "role_id", nullable = false)
    var roleId: Long = 0

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: java.time.Instant = java.time.Instant.now()
}
