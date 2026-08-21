package com.ntt.authservice.auth.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.application.port.out.DomainEvent
import com.ntt.authservice.auth.application.port.out.EventStorePort
import com.ntt.authservice.auth.application.port.out.OutboxPort
import com.ntt.authservice.auth.domain.event.EventEnvelope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Intermediary service for transactional event recording (DD-003).
 * Encapsulates: event envelope creation → event store persist → outbox insert.
 * All within the caller's existing @Transactional boundary.
 *
 * Reusable across all command handlers.
 *
 * FR-004: Event store persistence
 * FR-005: Correlation ID
 * FR-006: CloudEvents-inspired envelope
 * FR-007: Transactional outbox
 * FR-014: Metadata enrichment
 */
@Component
class EventService(
    private val eventStorePort: EventStorePort,
    private val outboxPort: OutboxPort,
    private val objectMapper: ObjectMapper,
    @Value("\${app.event.source:auth-service}") private val eventSource: String
) {

    private val log = LoggerFactory.getLogger(EventService::class.java)

    /**
     * Record a domain event transactionally:
     * 1. Wrap in EventEnvelope with metadata
     * 2. Persist to event store (same TX as caller)
     * 3. Insert into outbox for async Kafka relay (same TX as caller)
     */
    fun <T : DomainEvent> record(
        aggregateType: String,
        aggregateId: Long,
        event: T,
        topic: String,
        partitionKey: String,
        correlationId: String? = null
    ) {
        val resolvedCorrelationId = correlationId ?: UUID.randomUUID().toString()

        val envelope = EventEnvelope(
            type = event.eventType,
            source = eventSource,
            correlationId = resolvedCorrelationId,
            data = event
        )

        val nextSeq = eventStorePort.getNextSequenceNumber(aggregateType, aggregateId)
        val payloadJson = objectMapper.writeValueAsString(envelope)

        // 1. Persist to event store (same TX)
        eventStorePort.append(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = event.eventType,
            schemaVersion = envelope.schemaVersion,
            sequenceNumber = nextSeq,
            payload = payloadJson,
            metadata = "{}"
        )

        // 2. Insert outbox entry (same TX)
        outboxPort.insert(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = event.eventType,
            topic = topic,
            partitionKey = partitionKey,
            payload = payloadJson
        )

        log.debug("Event recorded: type={}, aggregate={}:{}, seq={}, correlationId={}",
            event.eventType, aggregateType, aggregateId, nextSeq, resolvedCorrelationId)
    }
}
