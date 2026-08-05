package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakeBaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Password history entry — tracks previous password hashes for reuse prevention.
 */
@Entity
@Table(name = "password_history")
class PasswordHistoryEntity : SnowflakeBaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "password_hash", nullable = false, length = 255)
    lateinit var passwordHash: String

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
