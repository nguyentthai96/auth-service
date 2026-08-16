package com.ntt.authservice.auth.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * GDPR Data Export entity (FR-009).
 * Tracks data export requests and file locations.
 */
@Entity
@Table(name = "account_data_exports")
class AccountDataExportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "status", nullable = false)
    var status: String = "PENDING"

    @Column(name = "format", nullable = false)
    var format: String = "JSON"

    @Column(name = "file_path")
    var filePath: String? = null

    @Column(name = "file_size_bytes")
    var fileSizeBytes: Long? = null

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.now()

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "download_count", nullable = false)
    var downloadCount: Int = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_EXPIRED = "EXPIRED"
    }
}
