package com.ntt.authservice.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the event outbox poller.
 * Mapped from application.yml: app.event.outbox.*
 *
 * FR-007: Transactional outbox configuration
 * FR-021: Timeout handling
 * FR-022: Retry mechanism configuration
 */
@ConfigurationProperties(prefix = "app.event.outbox")
data class OutboxProperties(
    /** Polling interval in milliseconds for the outbox poller. */
    val pollIntervalMs: Long = 100,

    /** Maximum number of outbox entries to process per poll cycle. */
    val batchSize: Int = 50,

    /** Maximum number of retries before marking an entry as FAILED. */
    val maxRetries: Int = 3,

    /** Kafka send timeout in milliseconds. */
    val kafkaTimeoutMs: Long = 5000
)
