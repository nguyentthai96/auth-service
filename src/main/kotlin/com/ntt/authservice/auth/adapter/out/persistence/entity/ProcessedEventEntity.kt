package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Processed event entity — consumer-side idempotency tracking (FR-008).
 * Each consumed event is recorded with its consumer group to prevent
 * duplicate processing on re-delivery.
 */
@Entity
@Table(name = "processed_events")
class ProcessedEventEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "event_id", nullable = false)
    var eventId: UUID = UUID.randomUUID()

    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = ""

    @Column(name = "consumer_group", nullable = false, length = 100)
    var consumerGroup: String = ""

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now()
}
