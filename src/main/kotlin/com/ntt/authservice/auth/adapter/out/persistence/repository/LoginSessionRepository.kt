package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface LoginSessionRepository : JpaRepository<LoginSessionEntity, Long> {

    fun findByUserIdAndSessionActiveTrue(userId: Long): List<LoginSessionEntity>

    fun countByUserIdAndSessionActiveTrue(userId: Long): Long

    fun findFirstByUserIdAndSessionActiveTrueOrderByLoginAtAsc(userId: Long): LoginSessionEntity?

    fun findByDeviceFingerprintAndUserId(deviceFingerprint: String, userId: Long): List<LoginSessionEntity>

    // FR-008: Auto-expire inactive sessions
    fun findBySessionActiveTrueAndLastActivityAtBefore(cutoff: Instant): List<LoginSessionEntity>

    // FR-008: Admin session listing (paginated)
    fun findBySessionActiveTrue(pageable: Pageable): Page<LoginSessionEntity>

    // FR-008: Count all active sessions
    fun countBySessionActiveTrue(): Long

    // FR-008: Session stats by device type
    @Query("SELECT s.deviceType, COUNT(s) FROM LoginSessionEntity s WHERE s.sessionActive = true GROUP BY s.deviceType")
    fun countActiveSessionsByDeviceType(): List<Array<Any>>
}
