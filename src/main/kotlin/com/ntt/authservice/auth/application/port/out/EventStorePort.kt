package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for event store persistence (FR-004, FR-009).
 * Follows existing port pattern (UserPort, DomainPort).
 *
 * The event store is append-only — no update/delete operations.
 */
interface EventStorePort {

    /**
     * Append an event to the event store.
     */
    fun append(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        schemaVersion: Int,
        sequenceNumber: Long,
        payload: String,
        metadata: String
    )

    /**
     * Find all events for an aggregate, ordered by sequence number ascending.
     */
    fun findByAggregate(aggregateType: String, aggregateId: Long): List<Any>

    /**
     * Get the next sequence number for an aggregate.
     * Returns 1 if no events exist for the aggregate.
     */
    fun getNextSequenceNumber(aggregateType: String, aggregateId: Long): Long
}
