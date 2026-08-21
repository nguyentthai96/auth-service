package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.MfaRecoveryCodeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for MFA recovery codes (FR-001).
 */
@Repository
interface MfaRecoveryCodeRepository : JpaRepository<MfaRecoveryCodeEntity, Long> {

    /** Find all unused recovery codes for a user. */
    fun findByUserIdAndUsedFalse(userId: Long): List<MfaRecoveryCodeEntity>

    /** Count remaining (unused) recovery codes for a user. */
    fun countByUserIdAndUsedFalse(userId: Long): Long

    /** Delete all recovery codes for a user (used before regeneration). */
    fun deleteByUserId(userId: Long)
}
