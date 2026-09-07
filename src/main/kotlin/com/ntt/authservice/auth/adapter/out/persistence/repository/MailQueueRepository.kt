package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.MailQueueEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * Repository for mail queue — supports polling with pessimistic locking (FOR UPDATE SKIP LOCKED).
 */
interface MailQueueRepository : JpaRepository<MailQueueEntity, Long> {

    /**
     * Find pending mails ready for processing.
     * Uses SELECT FOR UPDATE SKIP LOCKED to prevent concurrent processing.
     */
    @Query(
        value = """
            SELECT * FROM mail_queue 
            WHERE status = 'PENDING' 
              AND (next_retry_at IS NULL OR next_retry_at <= :now) 
            ORDER BY created_at ASC 
            LIMIT :batchSize 
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingForProcessing(
        @Param("now") now: Instant,
        @Param("batchSize") batchSize: Int
    ): List<MailQueueEntity>

    /**
     * Delete old sent mails for cleanup.
     */
    @Modifying
    @Query("DELETE FROM MailQueueEntity m WHERE m.status = 'SENT' AND m.sentAt < :cutoff")
    fun deleteOldSentMails(@Param("cutoff") cutoff: Instant): Int
}
