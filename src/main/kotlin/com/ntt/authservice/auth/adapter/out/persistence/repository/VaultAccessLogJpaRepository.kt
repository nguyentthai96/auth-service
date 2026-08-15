package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.VaultAccessLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * JPA repository for vault access log.
 */
@Repository
interface VaultAccessLogJpaRepository : JpaRepository<VaultAccessLogEntity, Long> {

    /**
     * Find pending requests for a specific requester.
     */
    fun findByRequesterIdAndStatus(requesterId: String, status: String): List<VaultAccessLogEntity>
}
