package com.ntt.authservice.shared.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

/**
 * Kafka configuration for dead-letter queue (FR-020).
 * Failed messages are retried 3 times with exponential backoff,
 * then sent to .DLT topic suffix.
 */
@Configuration
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
class KafkaConfig {

    private val log = LoggerFactory.getLogger(KafkaConfig::class.java)

    /**
     * Error handler with DLQ — max 3 retries, backoff multiplier 2.
     * Failed messages go to <original-topic>.DLT
     */
    @Bean
    fun kafkaErrorHandler(
        kafkaOperations: KafkaOperations<String, String>
    ): CommonErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaOperations) { record: ConsumerRecord<*, *>, _: Exception ->
            org.apache.kafka.common.TopicPartition("${record.topic()}.DLT", record.partition())
        }

        val backoff = ExponentialBackOff().apply {
            initialInterval = 1000  // 1 second
            multiplier = 2.0       // 1s, 2s, 4s
            maxInterval = 8000     // max 8s
            maxElapsedTime = 30000 // give up after 30s total
        }

        return DefaultErrorHandler(recoverer, backoff).apply {
            setRetryListeners({ record, ex, deliveryAttempt ->
                log.warn("Kafka retry attempt {} for topic={}: {}",
                    deliveryAttempt, record?.topic(), ex?.message)
            })
        }
    }
}
