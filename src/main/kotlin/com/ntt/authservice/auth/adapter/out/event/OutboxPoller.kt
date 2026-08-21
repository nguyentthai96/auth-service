package com.ntt.authservice.auth.adapter.out.event

import com.ntt.authservice.auth.adapter.out.persistence.entity.EventOutboxEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.EventOutboxJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Outbox poller — asynchronously relays pending outbox entries to Kafka (FR-007).
 *
 * Uses polling pattern with `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety (DD-006).
 * Only active when Kafka is configured (spring.kafka.bootstrap-servers).
 *
 * FR-007: Transactional outbox relay
 * FR-021: Timeout handling — Kafka timeout does NOT increment retry_count
 * FR-022: Retry mechanism — confirmed failures increment retry_count, exceed max → FAILED
 *
 * Reference: SessionCleanupScheduler.kt — same @Scheduled + @Transactional pattern
 */
@Component
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
class OutboxPoller(
    private val outboxRepository: EventOutboxJpaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${app.event.outbox.batch-size:50}") private val batchSize: Int,
    @Value("\${app.event.outbox.max-retries:3}") private val maxRetries: Int,
    @Value("\${app.event.outbox.kafka-timeout-ms:5000}") private val kafkaTimeoutMs: Long
) {

    private val log = LoggerFactory.getLogger(OutboxPoller::class.java)

    /**
     * Poll for pending outbox entries and publish to Kafka.
     * Runs at fixed delay (default 100ms) — configurable via app.event.outbox.poll-interval-ms.
     */
    @Scheduled(fixedDelayString = "\${app.event.outbox.poll-interval-ms:100}")
    @Transactional
    fun pollAndPublish() {
        val pendingEntries = outboxRepository.findPendingForUpdate(batchSize)
        if (pendingEntries.isEmpty()) return

        log.debug("OUTBOX_POLL Processing {} pending entries", pendingEntries.size)

        for (entry in pendingEntries) {
            publishEntry(entry)
        }
    }

    /**
     * Publish a single outbox entry to Kafka.
     * - On success: mark PUBLISHED with timestamp
     * - On Kafka timeout: entry remains PENDING, NOT incrementing retry_count (FR-021)
     * - On confirmed failure: increment retry_count; if >= maxRetries → mark FAILED (FR-022)
     */
    private fun publishEntry(entry: EventOutboxEntity) {
        try {
            val future = kafkaTemplate.send(entry.topic, entry.partitionKey, entry.payload)

            // Block with timeout to detect Kafka issues (FR-021)
            val result = future.get(kafkaTimeoutMs, TimeUnit.MILLISECONDS)

            // Success — mark as PUBLISHED
            entry.status = "PUBLISHED"
            entry.publishedAt = Instant.now()
            outboxRepository.save(entry)

            log.debug("OUTBOX_PUBLISHED id={}, topic={}, offset={}",
                entry.id, entry.topic, result?.recordMetadata?.offset())

        } catch (e: java.util.concurrent.TimeoutException) {
            // Kafka timeout — entry remains PENDING, will be retried next cycle (FR-021)
            // Do NOT increment retry_count for timeouts
            log.warn("OUTBOX_TIMEOUT id={}, topic={}, timeoutMs={} — will retry next cycle",
                entry.id, entry.topic, kafkaTimeoutMs)

        } catch (e: Exception) {
            // Confirmed failure — increment retry count (FR-022)
            entry.retryCount += 1
            if (entry.retryCount >= maxRetries) {
                entry.status = "FAILED"
                outboxRepository.save(entry)
                log.error("OUTBOX_MAX_RETRIES_EXCEEDED id={}, topic={}, retryCount={} — marked FAILED",
                    entry.id, entry.topic, entry.retryCount, e)
            } else {
                outboxRepository.save(entry)
                log.warn("OUTBOX_SEND_FAILED id={}, topic={}, retryCount={}/{} — will retry",
                    entry.id, entry.topic, entry.retryCount, maxRetries, e)
            }
        }
    }
}
