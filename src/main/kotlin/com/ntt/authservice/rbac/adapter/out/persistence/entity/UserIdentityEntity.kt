package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakeBaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * SSO identity linking — maps external IdP users to internal UserEntity.
 */
@Entity
@Table(
    name = "user_identities",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_sub"])]
)
class UserIdentityEntity : SnowflakeBaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(nullable = false, length = 50)
    lateinit var provider: String

    @Column(name = "provider_sub", nullable = false, length = 255)
    lateinit var providerSub: String

    @Column(name = "provider_email", length = 255)
    var providerEmail: String? = null

    @Column(name = "provider_name", length = 200)
    var providerName: String? = null

    @Column(name = "linked_at", nullable = false)
    var linkedAt: Instant = Instant.now()

    @Column(nullable = false)
    var active: Boolean = true

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
