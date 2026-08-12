package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LoginSessionRepository : JpaRepository<LoginSessionEntity, Long> {

    fun findByUserIdAndSessionActiveTrue(userId: Long): List<LoginSessionEntity>

    fun countByUserIdAndSessionActiveTrue(userId: Long): Long

    fun findFirstByUserIdAndSessionActiveTrueOrderByLoginAtAsc(userId: Long): LoginSessionEntity?

    fun findByDeviceFingerprintAndUserId(deviceFingerprint: String, userId: Long): List<LoginSessionEntity>
}
