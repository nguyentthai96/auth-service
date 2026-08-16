package com.ntt.authservice.auth.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * GDPR Account Deletion Request entity (FR-009).
 * Tracks user deletion requests with grace period.
 */
@Entity
@Table(name = "account_deletion_requests")
class AccountDeletionRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "reason")
    var reason: String? = null

    @Column(name = "status", nullable = false)
    var status: String = "PENDING"

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.now()

    @Column(name = "scheduled_delete_at", nullable = false)
    var scheduledDeleteAt: Instant = Instant.now()

    @Column(name = "processed_at")
    var processedAt: Instant? = null

    @Column(name = "processed_by")
    var processedBy: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
