package com.ntt.authservice.auth.adapter.out.persistence

import com.ntt.authservice.auth.adapter.out.persistence.entity.EventStoreEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.EventStoreJpaRepository
import com.ntt.authservice.auth.application.port.out.EventStorePort
import com.ntt.authservice.shared.exception.EventStorePersistException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * JPA adapter implementing EventStorePort — persists events to event_store table (FR-004).
 * Follows existing adapter pattern (UserPersistenceAdapter).
 */
@Component
class EventStorePersistenceAdapter(
    private val eventStoreJpaRepository: EventStoreJpaRepository
) : EventStorePort {

    private val log = LoggerFactory.getLogger(EventStorePersistenceAdapter::class.java)

    override fun append(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        schemaVersion: Int,
        sequenceNumber: Long,
        payload: String,
        metadata: String
    ) {
        try {
            val entity = EventStoreEntity().apply {
                this.aggregateType = aggregateType
                this.aggregateId = aggregateId
                this.eventType = eventType
                this.schemaVersion = schemaVersion
                this.sequenceNumber = sequenceNumber
                this.payload = payload
                this.metadata = metadata
            }
            eventStoreJpaRepository.save(entity)
            log.debug("Event appended to store: type={}, aggregate={}:{}, seq={}",
                eventType, aggregateType, aggregateId, sequenceNumber)
        } catch (e: Exception) {
            log.error("Failed to persist event: type={}, aggregate={}:{}",
                eventType, aggregateType, aggregateId, e)
            throw EventStorePersistException("Failed to persist event: ${e.message}")
        }
    }

    override fun findByAggregate(aggregateType: String, aggregateId: Long): List<Any> {
        return eventStoreJpaRepository.findByAggregateTypeAndAggregateIdOrderBySequenceNumberAsc(
            aggregateType, aggregateId
        )
    }

    override fun getNextSequenceNumber(aggregateType: String, aggregateId: Long): Long {
        val maxSeq = eventStoreJpaRepository.findMaxSequenceNumber(aggregateType, aggregateId)
        return (maxSeq ?: 0) + 1
    }
}
