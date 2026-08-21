package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.EventOutboxEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * JPA repository for event outbox — transactional outbox pattern (FR-007).
 */
@Repository
interface EventOutboxJpaRepository : JpaRepository<EventOutboxEntity, Long> {

    /**
     * Find pending outbox entries for processing with pessimistic locking.
     * Uses SELECT ... FOR UPDATE SKIP LOCKED for multi-instance safety (DD-006).
     */
    @Query(
        value = "SELECT * FROM event_outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    fun findPendingForUpdate(limit: Int): List<EventOutboxEntity>
}
