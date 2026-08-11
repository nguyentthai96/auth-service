package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LoginSessionRepository : JpaRepository<LoginSessionEntity, Long> {

    fun findByUserIdAndIsActiveTrue(userId: Long): List<LoginSessionEntity>

    fun countByUserIdAndIsActiveTrue(userId: Long): Long

    fun findFirstByUserIdAndIsActiveTrueOrderByLoginAtAsc(userId: Long): LoginSessionEntity?

    fun findByDeviceFingerprintAndUserId(deviceFingerprint: String, userId: Long): List<LoginSessionEntity>
}
