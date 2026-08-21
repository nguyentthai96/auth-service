package com.ntt.authservice.shared.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for audit log persistence (FR-015).
 * Note: audit_logs table has DB rules preventing UPDATE/DELETE.
 */
@Repository
interface AuditLogRepository : JpaRepository<AuditLogEntity, Long> {

    /** Find audit logs by user ID (paginated). */
    fun findByUserIdOrderByEventTimestampDesc(userId: Long, pageable: Pageable): Page<AuditLogEntity>

    /** Find audit logs by action type. */
    fun findByActionOrderByEventTimestampDesc(action: String, pageable: Pageable): Page<AuditLogEntity>

    /** Find audit logs for a specific entity. */
    fun findByEntityTypeAndEntityIdOrderByEventTimestampDesc(entityType: String, entityId: String): List<AuditLogEntity>

    /** Find audit logs within a time range. */
    fun findByEventTimestampBetweenOrderByEventTimestampDesc(start: Instant, end: Instant, pageable: Pageable): Page<AuditLogEntity>
}
