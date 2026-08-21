package com.ntt.authservice.auth.adapter.out.persistence

import com.ntt.authservice.auth.adapter.out.persistence.entity.EventOutboxEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.EventOutboxJpaRepository
import com.ntt.authservice.auth.application.port.out.OutboxPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * JPA adapter implementing OutboxPort — manages transactional outbox entries (FR-007).
 * Follows existing adapter pattern (UserPersistenceAdapter).
 */
@Component
class OutboxPersistenceAdapter(
    private val eventOutboxJpaRepository: EventOutboxJpaRepository
) : OutboxPort {

    private val log = LoggerFactory.getLogger(OutboxPersistenceAdapter::class.java)

    override fun insert(
        aggregateType: String,
        aggregateId: Long,
        eventType: String,
        topic: String,
        partitionKey: String,
        payload: String
    ) {
        val entity = EventOutboxEntity().apply {
            this.aggregateType = aggregateType
            this.aggregateId = aggregateId
            this.eventType = eventType
            this.topic = topic
            this.partitionKey = partitionKey
            this.payload = payload
            this.status = "PENDING"
        }
        eventOutboxJpaRepository.save(entity)
        log.debug("Outbox entry created: topic={}, aggregate={}:{}", topic, aggregateType, aggregateId)
    }

    override fun findPendingForUpdate(limit: Int): List<Any> {
        return eventOutboxJpaRepository.findPendingForUpdate(limit)
    }

    override fun markPublished(id: Long) {
        eventOutboxJpaRepository.findById(id).ifPresent { entity ->
            entity.status = "PUBLISHED"
            entity.publishedAt = Instant.now()
            eventOutboxJpaRepository.save(entity)
        }
    }

    override fun markFailed(id: Long) {
        eventOutboxJpaRepository.findById(id).ifPresent { entity ->
            entity.status = "FAILED"
            eventOutboxJpaRepository.save(entity)
            log.error("Outbox entry permanently failed: id={}, topic={}, eventType={}",
                id, entity.topic, entity.eventType)
        }
    }

    override fun incrementRetryCount(id: Long) {
        eventOutboxJpaRepository.findById(id).ifPresent { entity ->
            entity.retryCount += 1
            eventOutboxJpaRepository.save(entity)
            log.warn("Outbox entry retry count incremented: id={}, retryCount={}", id, entity.retryCount)
        }
    }
}
