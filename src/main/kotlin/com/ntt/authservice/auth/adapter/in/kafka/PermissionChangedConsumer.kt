package com.ntt.authservice.auth.adapter.`in`.kafka

import com.ntt.authservice.auth.application.port.out.PermissionCache
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for permission change events.
 * Invalidates L1+L2 caches when admin modifies role/permission assignments.
 *
 * Topic: iam.permission.changed
 * Active when: spring.kafka.bootstrap-servers is configured
 */
@Component
@ConditionalOnProperty(name = ["spring.kafka.bootstrap-servers"])
class PermissionChangedConsumer(
    private val permissionCache: PermissionCache
) {

    private val log = LoggerFactory.getLogger(PermissionChangedConsumer::class.java)

    @KafkaListener(topics = ["iam.permission.changed"], groupId = "auth-service")
    fun onPermissionChanged(message: String) {
        log.info("Received permission change event: {}", message)

        try {
            // Parse event — expected format: {"userId": 123, "domainId": 456}
            // For MVP, invalidate all caches for simplicity
            // TODO: Parse JSON and invalidate specific user+domain
            permissionCache.invalidateAll()
            log.info("Permission caches invalidated after change event")
        } catch (ex: Exception) {
            log.error("Failed to process permission change event: {}", ex.message, ex)
        }
    }
}
