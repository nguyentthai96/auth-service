package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.EventStoreEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * JPA repository for event store — append-only event log (FR-004).
 */
@Repository
interface EventStoreJpaRepository : JpaRepository<EventStoreEntity, Long> {

    /**
     * Find all events for an aggregate, ordered by sequence number ascending.
     */
    fun findByAggregateTypeAndAggregateIdOrderBySequenceNumberAsc(
        aggregateType: String,
        aggregateId: Long
    ): List<EventStoreEntity>

    /**
     * Get the maximum sequence number for an aggregate.
     * Returns null if no events exist.
     */
    @Query("SELECT MAX(e.sequenceNumber) FROM EventStoreEntity e WHERE e.aggregateType = :aggregateType AND e.aggregateId = :aggregateId")
    fun findMaxSequenceNumber(aggregateType: String, aggregateId: Long): Long?
}
