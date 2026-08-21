package com.ntt.authservice.shared.audit

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * Audit log entity (FR-015) — immutable audit trail for security events.
 * Stored in audit_logs table with JSONB details.
 * Database rules prevent UPDATE/DELETE operations on this table.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = [
        Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
        Index(name = "idx_audit_logs_action", columnList = "action"),
        Index(name = "idx_audit_logs_entity_type_id", columnList = "entity_type, entity_id"),
        Index(name = "idx_audit_logs_timestamp", columnList = "event_timestamp")
    ]
)
class AuditLogEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id")
    var userId: Long? = null

    @Column(name = "action", nullable = false, length = 50)
    lateinit var action: String

    @Column(name = "entity_type", length = 100)
    var entityType: String? = null

    @Column(name = "entity_id", length = 100)
    var entityId: String? = null

    /** JSONB details — may contain masked sensitive data. */
    @Column(name = "details", columnDefinition = "jsonb")
    var details: String? = null

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null

    @Column(name = "event_timestamp", nullable = false)
    lateinit var eventTimestamp: Instant
}
