package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Event store entity — append-only immutable event log (FR-004).
 * NOT using VersionedAuditableEntity — event store has no optimistic locking.
 */
@Entity
@Table(name = "event_store")
class EventStoreEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "aggregate_type", nullable = false, length = 100)
    var aggregateType: String = ""

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long = 0

    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = ""

    @Column(name = "schema_version", nullable = false)
    var schemaVersion: Int = 1

    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Long = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}"

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    var metadata: String = "{}"
}
