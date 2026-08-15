package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.CipherKeySessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * JPA repository for cipher key sessions.
 * Provides DB fallback storage (Redis is primary).
 */
@Repository
interface CipherKeySessionJpaRepository : JpaRepository<CipherKeySessionEntity, String> {

    /**
     * Find active session for user + device combination.
     * Used for idempotent key exchange (same device → return existing session).
     */
    fun findByUserIdAndDeviceIdAndIsActiveTrue(userId: String, deviceId: String): CipherKeySessionEntity?

    /**
     * Count active sessions for a user (for max-devices enforcement).
     */
    fun countByUserIdAndIsActiveTrue(userId: String): Int

    /**
     * Deactivate all sessions for a user on a specific device.
     * Called before creating new session (rotate old keys).
     */
    @Modifying
    @Query("UPDATE CipherKeySessionEntity e SET e.isActive = false WHERE e.userId = :userId AND e.deviceId = :deviceId AND e.isActive = true")
    fun deactivateByUserIdAndDeviceId(userId: String, deviceId: String)

    /**
     * Deactivate a specific session by keyId.
     */
    @Modifying
    @Query("UPDATE CipherKeySessionEntity e SET e.isActive = false WHERE e.keyId = :keyId")
    fun deactivateByKeyId(keyId: String)
}
