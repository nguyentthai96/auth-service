package com.ntt.auth.client.entity

import jakarta.persistence.*
import org.hibernate.annotations.Immutable

/**
 * Read-only projection of `user_groups` join table.
 */
@Entity
@Immutable
@Table(name = "user_groups")
class UserGroupReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "group_id", nullable = false)
    var groupId: Long = 0
}

/**
 * Read-only projection of `group_roles` join table.
 */
@Entity
@Immutable
@Table(name = "group_roles")
class GroupRoleReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "group_id", nullable = false)
    var groupId: Long = 0

    @Column(name = "role_id", nullable = false)
    var roleId: Long = 0
}

/**
 * Read-only projection of `permissions` table (Resource × Action join).
 */
@Entity
@Immutable
@Table(name = "permissions")
class PermissionReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "resource_id", nullable = false)
    var resourceId: Long = 0

    @Column(name = "action_id", nullable = false)
    var actionId: Long = 0
}

/**
 * Read-only projection of `role_permissions` table.
 */
@Entity
@Immutable
@Table(name = "role_permissions")
class RolePermissionReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "role_id", nullable = false)
    var roleId: Long = 0

    @Column(name = "permission_id", nullable = false)
    var permissionId: Long = 0
}

/**
 * Read-only projection of `resources` table.
 */
@Entity
@Immutable
@Table(name = "resources")
class ResourceReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "code", length = 50, nullable = false)
    var code: String = ""

    @Column(name = "name", length = 200, nullable = false)
    var name: String = ""
}

/**
 * Read-only projection of `actions` table.
 */
@Entity
@Immutable
@Table(name = "actions")
class ActionReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "code", length = 50, nullable = false)
    var code: String = ""

    @Column(name = "name", length = 100, nullable = false)
    var name: String = ""
}
