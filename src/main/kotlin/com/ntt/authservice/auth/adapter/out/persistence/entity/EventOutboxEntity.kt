package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Event outbox entity — transactional outbox for reliable Kafka publishing (FR-007).
 * Entries are created in the same transaction as the aggregate state change,
 * then relayed to Kafka asynchronously by OutboxPoller.
 */
@Entity
@Table(name = "event_outbox")
class EventOutboxEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "aggregate_type", nullable = false, length = 100)
    var aggregateType: String = ""

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long = 0

    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = ""

    @Column(name = "topic", nullable = false, length = 200)
    var topic: String = ""

    @Column(name = "partition_key", nullable = false, length = 200)
    var partitionKey: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}"

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "PENDING"

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "published_at")
    var publishedAt: Instant? = null
}
