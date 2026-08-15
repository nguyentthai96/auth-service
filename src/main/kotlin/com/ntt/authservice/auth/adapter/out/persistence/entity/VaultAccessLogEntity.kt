package com.ntt.authservice.auth.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * JPA entity for vault_access_log table.
 * Tracks break-glass decryption requests (4-eyes approval workflow).
 */
@Entity
@Table(name = "vault_access_log")
class VaultAccessLogEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "requester_id", length = 100, nullable = false)
    val requesterId: String = "",

    @Column(name = "approver_id", length = 100)
    var approverId: String? = null,

    @Column(name = "request_type", length = 50, nullable = false)
    val requestType: String = "",

    @Column(name = "status", length = 20, nullable = false)
    var status: String = "PENDING",

    @Column(name = "target_audit_id")
    val targetAuditId: Long? = null,

    @Column(name = "decrypt_count", nullable = false)
    var decryptCount: Int = 0,

    @Column(name = "max_decrypts", nullable = false)
    val maxDecrypts: Int = 10,

    @Column(name = "expires_at")
    var expiresAt: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = 0L,

    @Column(name = "approved_at")
    var approvedAt: Long? = null
)
