package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.ProcessedEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * JPA repository for processed events — consumer idempotency (FR-008).
 */
@Repository
interface ProcessedEventJpaRepository : JpaRepository<ProcessedEventEntity, Long> {

    /**
     * Check if an event has already been processed by a consumer group.
     */
    fun existsByEventIdAndConsumerGroup(eventId: UUID, consumerGroup: String): Boolean
}
