package com.ntt.authservice.auth.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * JPA entity for cipher_key_session table.
 * Maps 1:1 with CipherKeySession data class (base-core).
 */
@Entity
@Table(
    name = "cipher_key_session",
    indexes = [
        Index(name = "idx_key_session_user_device", columnList = "user_id, device_id"),
        Index(name = "idx_key_session_expires", columnList = "expires_at")
    ]
)
class CipherKeySessionEntity(

    @Id
    @Column(name = "key_id", length = 36)
    val keyId: String = "",

    @Column(name = "user_id", length = 100)
    val userId: String? = null,

    @Column(name = "device_id", length = 200, nullable = false)
    val deviceId: String = "",

    @Column(name = "platform", length = 20, nullable = false)
    val platform: String = "",

    @Column(name = "algorithm_id", length = 50, nullable = false)
    val algorithmId: String = "",

    @Column(name = "client_to_server_key", nullable = false, columnDefinition = "BYTEA")
    val clientToServerKey: ByteArray = ByteArray(0),

    @Column(name = "server_to_client_key", nullable = false, columnDefinition = "BYTEA")
    val serverToClientKey: ByteArray = ByteArray(0),

    @Column(name = "key_version", nullable = false)
    val keyVersion: Int = 1,

    @Column(name = "app_version", length = 50, nullable = false)
    val appVersion: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = 0L,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Long = 0L,

    @Column(name = "is_active")
    var isActive: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CipherKeySessionEntity) return false
        return keyId == other.keyId
    }

    override fun hashCode(): Int = keyId.hashCode()
}
