package com.ntt.authservice.auth.domain.event

import java.time.Instant
import java.util.UUID

/**
 * CloudEvents-inspired generic event envelope.
 * Wraps all domain events with standardized metadata for interoperability.
 *
 * Follows CloudEvents 1.0 structure without SDK dependency (DD-002).
 * FR-006: CloudEvents-inspired envelope
 * FR-003: Schema versioning
 * FR-005: Correlation ID for tracing
 * FR-014: Metadata enrichment
 */
data class EventEnvelope<T>(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val source: String = "auth-service",
    val specversion: String = "1.0",
    val time: Instant = Instant.now(),
    val correlationId: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = 1,
    val data: T
)
