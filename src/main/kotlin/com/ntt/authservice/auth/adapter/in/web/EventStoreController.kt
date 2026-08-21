package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.port.out.EventStorePort
import com.ntt.authservice.shared.exception.EventNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Internal API controller for querying events from the event store (FR-009).
 * Protected by ServiceAuthFilter — requires service JWT for inter-service calls.
 *
 * Follows InternalApiController.kt pattern — internal endpoints under /api/internal.
 */
@RestController
@RequestMapping("/api/internal/events")
class EventStoreController(
    private val eventStorePort: EventStorePort
) {

    /**
     * Query events for a specific aggregate, ordered by sequence number ascending.
     * Returns events as raw JSON payloads (EventEnvelope-wrapped).
     *
     * @param aggregateType Aggregate type (e.g., "User")
     * @param aggregateId Aggregate ID (e.g., userId)
     * @return List of event store entries ordered by sequence number
     */
    @GetMapping("/{aggregateType}/{aggregateId}")
    fun getEventsByAggregate(
        @PathVariable aggregateType: String,
        @PathVariable aggregateId: Long
    ): ResponseEntity<List<Any>> {
        val events = eventStorePort.findByAggregate(aggregateType, aggregateId)
        if (events.isEmpty()) {
            throw EventNotFoundException(aggregateType, aggregateId)
        }
        return ResponseEntity.ok(events)
    }
}
