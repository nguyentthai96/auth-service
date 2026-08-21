package com.ntt.authservice.auth.adapter.out.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import com.ntt.authservice.auth.application.port.out.EventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

/**
 * Kafka event publisher adapter (FR-020).
 * Replaces SpringEventPublisher — publishes domain events to Kafka topics.
 * Retry: max 3 attempts with exponential backoff (1s, 2s, 4s).
 *
 * Activated when spring.kafka.bootstrap-servers is configured.
 */
@Component
@Primary
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
class KafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : EventPublisher {

    private val log = LoggerFactory.getLogger(KafkaEventPublisher::class.java)

    companion object {
        private const val TOPIC_PREFIX = "iam."
    }

    /**
     * Publish a domain event to Kafka with retry.
     * Topic is derived from event type with iam. prefix normalization (FR-010).
     * Events already prefixed with "iam." are not double-prefixed.
     *
     * Note: UserRegisteredEvent now goes through outbox path (OutboxPoller).
     * This publisher is still used for non-outbox events (AuditEvent, PermissionChangedEvent, etc.).
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000)
    )
    override fun publish(event: DomainEvent) {
        val topic = if (event.eventType.startsWith(TOPIC_PREFIX)) {
            event.eventType
        } else {
            TOPIC_PREFIX + event.eventType
        }
        val payload = objectMapper.writeValueAsString(event)

        try {
            val future = kafkaTemplate.send(topic, payload)
            future.whenComplete { result, ex ->
                if (ex != null) {
                    log.error("Failed to publish event to Kafka: topic={}, type={}, error={}",
                        topic, event.eventType, ex.message, ex)
                } else {
                    log.info("Event published to Kafka: topic={}, type={}, offset={}",
                        topic, event.eventType, result?.recordMetadata?.offset())
                }
            }
        } catch (e: Exception) {
            log.error("Kafka publish failed (will retry): topic={}, type={}", topic, event.eventType, e)
            throw e
        }
    }
}
