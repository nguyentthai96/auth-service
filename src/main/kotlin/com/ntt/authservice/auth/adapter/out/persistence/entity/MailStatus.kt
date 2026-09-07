package com.ntt.authservice.auth.adapter.out.persistence.entity

/**
 * Status lifecycle for mail queue records.
 * PENDING → PROCESSING → SENT | FAILED
 */
enum class MailStatus {
    /** Awaiting processing by MailJobScheduler. */
    PENDING,
    /** Currently being processed (locked by SELECT FOR UPDATE SKIP LOCKED). */
    PROCESSING,
    /** Successfully sent via SMTP. */
    SENT,
    /** Failed after max retries exhausted. */
    FAILED
}
