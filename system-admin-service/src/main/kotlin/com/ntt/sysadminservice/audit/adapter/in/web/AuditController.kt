package com.ntt.sysadminservice.audit.adapter.`in`.web

import com.ntt.sysadminservice.audit.adapter.out.persistence.entity.AuditLogEntity
import com.ntt.sysadminservice.audit.application.AuditService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * Audit controller — REST endpoints for audit log access (FR-015).
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditController(
    private val auditService: AuditService
) {

    @GetMapping
    fun searchAuditLogs(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        pageable: Pageable
    ): ResponseEntity<Page<AuditLogEntity>> {
        return ResponseEntity.ok(auditService.searchAuditLogs(userId, action, entityType, from, to, pageable))
    }

    @GetMapping("/export")
    fun exportAuditLogs(
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "10000") limit: Int
    ): ResponseEntity<List<AuditLogEntity>> {
        return ResponseEntity.ok(auditService.exportAuditLogs(from, to, limit))
    }
}
