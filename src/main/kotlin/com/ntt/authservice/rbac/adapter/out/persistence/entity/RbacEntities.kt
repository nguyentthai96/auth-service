package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Group (global scope — domain_id removed).
 */
@Entity
@Table(name = "groups", uniqueConstraints = [UniqueConstraint(columnNames = ["name"])])
class GroupEntity : SnowflakePersistentAuditableEntity() {

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
 * Role (global scope — renamed from DomainRoleEntity, domain_id removed).
 */
@Entity
@Table(name = "roles", uniqueConstraints = [UniqueConstraint(columnNames = ["code"])])
class RoleEntity : SnowflakePersistentAuditableEntity() {

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
