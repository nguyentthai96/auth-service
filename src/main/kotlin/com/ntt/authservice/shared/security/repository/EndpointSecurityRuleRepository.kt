package com.ntt.authservice.shared.security.repository

import com.ntt.authservice.shared.security.entity.EndpointSecurityRuleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EndpointSecurityRuleRepository : JpaRepository<EndpointSecurityRuleEntity, Long> {

    @Query("SELECT r FROM EndpointSecurityRuleEntity r WHERE r.isActive = true ORDER BY r.sortOrder ASC")
    fun findAllActiveOrderBySortOrder(): List<EndpointSecurityRuleEntity>

    @Query("""
        SELECT r FROM EndpointSecurityRuleEntity r 
        WHERE r.isActive = true 
          AND (r.domainScope IS NULL OR r.domainScope = :domainScope)
        ORDER BY r.sortOrder ASC
    """)
    fun findActiveByDomainScope(domainScope: String): List<EndpointSecurityRuleEntity>
}
