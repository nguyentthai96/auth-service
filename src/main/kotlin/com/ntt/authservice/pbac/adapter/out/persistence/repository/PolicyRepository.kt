package com.ntt.authservice.pbac.adapter.out.persistence.repository

import com.ntt.authservice.pbac.adapter.out.persistence.entity.PolicyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

// Re-export for application layer usage
typealias PolicyRepository = PolicyJpaRepository

@Repository
interface PolicyJpaRepository : JpaRepository<PolicyEntity, Long> {

    fun findAllByStatusAndActiveTrue(status: String = "ACTIVE"): List<PolicyEntity>

    @Query("""
        SELECT p FROM PolicyEntity p 
        WHERE p.status = 'ACTIVE' 
          AND p.active = true
          AND (p.resourceId = :resourceId OR p.resourceId IS NULL)
          AND (p.actionId = :actionId OR p.actionId IS NULL)
        ORDER BY p.priority ASC
    """)
    fun findApplicablePolicies(resourceId: Long?, actionId: Long?): List<PolicyEntity>
}
