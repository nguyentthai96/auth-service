package com.ntt.auth.client.entity

import jakarta.persistence.*
import org.hibernate.annotations.Immutable

/**
 * Read-only projection of `users` table.
 * Maps only the fields needed for cross-service user lookups.
 * Does NOT include password, MFA secrets, or other sensitive fields.
 */
@Entity
@Immutable
@Table(name = "users")
class UserReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "username", length = 100, nullable = false)
    var username: String = ""

    @Column(name = "email", length = 255, nullable = false)
    var email: String = ""

    @Column(name = "full_name", length = 200, nullable = false)
    var fullName: String = ""

    @Column(name = "phone", length = 20)
    var phone: String? = null

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null

    @Column(name = "status", length = 20, nullable = false)
    var status: String = "ACTIVE"
}

/**
 * Read-only projection of `roles` table.
 */
@Entity
@Immutable
@Table(name = "roles")
class RoleReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "code", length = 50, nullable = false)
    var code: String = ""

    @Column(name = "name", length = 200, nullable = false)
    var name: String = ""

    @Column(name = "description")
    var description: String? = null

    @Column(name = "hierarchy_level", nullable = false)
    var hierarchyLevel: Int = 99

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false
}

/**
 * Read-only projection of `groups` table.
 */
@Entity
@Immutable
@Table(name = "groups")
class GroupReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "name", length = 200, nullable = false)
    var name: String = ""

    @Column(name = "description")
    var description: String? = null
}
