package com.ntt.authservice.auth.adapter.`in`.kafka

import org.springframework.cache.CacheManager
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
    private val cacheManager: CacheManager
) {

    private val log = LoggerFactory.getLogger(PermissionChangedConsumer::class.java)

    @KafkaListener(topics = ["iam.permission.changed"], groupId = "auth-service")
    fun onPermissionChanged(message: String) {
        log.info("Received permission change event: {}", message)

        try {
            // Parse event — expected format: {"userId": 123, "domainId": 456}
            // For MVP, invalidate all caches for simplicity
            // TODO: Parse JSON and invalidate specific user+domain
            cacheManager.getCache("permissions")?.clear()
            cacheManager.getCache("roles")?.clear()
            log.info("Permission caches invalidated after change event")
        } catch (ex: Exception) {
            log.error("Failed to process permission change event: {}", ex.message, ex)
        }
    }
}
