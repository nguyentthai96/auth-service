package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for transactional outbox operations (FR-007).
 * Follows existing port pattern (UserPort, DomainPort).
 */
interface OutboxPort {

    /**
     * Insert a new outbox entry with PENDING status.
     */
    fun insert(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        topic: String,
        partitionKey: String,
        payload: String
    )

    /**
     * Find pending outbox entries for processing.
     * Uses SELECT ... FOR UPDATE SKIP LOCKED for multi-instance safety.
     */
    fun findPendingForUpdate(limit: Int): List<Any>

    /**
     * Mark an outbox entry as successfully published.
     */
    fun markPublished(id: Long)

    /**
     * Mark an outbox entry as permanently failed.
     */
    fun markFailed(id: Long)

    /**
     * Increment the retry count for a failed outbox entry.
     */
    fun incrementRetryCount(id: Long)
}
