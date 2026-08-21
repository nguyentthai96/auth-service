package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * MFA Recovery Code entity (FR-001).
 * Stores SHA-256 hashed recovery codes for backup MFA authentication.
 * Each code is single-use — marked `used=true` after successful verification.
 * 10 codes generated per user on MFA setup or regeneration.
 */
@Entity
@Table(
    name = "mfa_recovery_codes",
    indexes = [Index(name = "idx_mfa_recovery_codes_user_id", columnList = "user_id")]
)
class MfaRecoveryCodeEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    /** SHA-256 hash of the recovery code (plain code shown once to user). */
    @Column(name = "code_hash", nullable = false, length = 64)
    lateinit var codeHash: String

    /** Whether this code has been used (single-use). */
    @Column(name = "used", nullable = false)
    var used: Boolean = false

    /** Timestamp when the code was used. */
    @Column(name = "used_at")
    var usedAt: Instant? = null
}
