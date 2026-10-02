package com.ntt.authservice.auth.adapter.`in`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.CacheManager
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for permission change events.
 * Invalidates L1+L2 caches when admin modifies role/permission assignments.
 *
 * Supports targeted invalidation: parses event JSON to evict specific user keys.
 * Falls back to full cache clear when event payload is incomplete or unparseable.
 *
 * Topic: configurable via app.kafka.topics.permission-changed (default: iam.permission.changed)
 * Active when: spring.kafka.bootstrap-servers is configured
 * Domain logic removed — global RBAC scope.
 */
@Component
@ConditionalOnProperty(name = ["spring.kafka.bootstrap-servers"])
class PermissionChangedConsumer(
    private val cacheManager: CacheManager,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(PermissionChangedConsumer::class.java)

    @KafkaListener(
        topics = ["\${app.kafka.topics.permission-changed:iam.permission.changed}"],
        groupId = "auth-service"
    )
    fun onPermissionChanged(message: String) {
        log.info("Received permission change event: {}", message)

        try {
            val event = objectMapper.readTree(message)
            val userId = event.path("userId").asLong(0L)

            if (userId > 0) {
                // Targeted invalidation — evict only affected user
                val permKey = "$userId"
                cacheManager.getCache("permissions")?.evict(permKey)
                cacheManager.getCache("roles")?.evict(permKey)
                log.info("Targeted cache invalidation: userId={}", userId)
            } else {
                // Incomplete payload — fallback to full invalidation
                fullCacheClear("incomplete event payload (userId=$userId)")
            }
        } catch (ex: Exception) {
            // Graceful degradation: full clear on parse error
            fullCacheClear("parse error: ${ex.message}")
            log.error("Failed to parse permission change event", ex)
        }
    }

    private fun fullCacheClear(reason: String) {
        cacheManager.getCache("permissions")?.clear()
        cacheManager.getCache("roles")?.clear()
        log.warn("Full cache invalidation — {}", reason)
    }
}
