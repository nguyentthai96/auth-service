package com.ntt.authservice.auth.adapter.`in`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.shared.security.MenuSecurityRuleMapper
import com.ntt.authservice.shared.security.MenuRuleRedisPublisher
import com.ntt.authservice.shared.security.UserMenuPermissionComputer
import com.ntt.authservice.shared.security.event.MenuPermissionChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for menu permission change events from system-admin-service.
 *
 * Listens to topic: menu.permission.changed
 * When menu permissions change:
 *   1. Deserialize event
 *   2. Map event to endpoint_security_rules (via MenuSecurityRuleMapper)
 *   3. Publish updated rules to Redis (via MenuRuleRedisPublisher)
 *   4. Recompute user permissions async (via UserMenuPermissionComputer)
 *
 * Guards applied by MenuSecurityRuleMapper:
 *   G1: Skip if assignedRoles empty (prevent lockout)
 *   G3: Skip if path is null or doesn't start with /api/
 *
 * Reuses the same pattern as AccountStatusConsumer.
 */
@Component
@ConditionalOnProperty(name = ["spring.kafka.bootstrap-servers"])
class MenuSecurityRuleSyncConsumer(
    private val objectMapper: ObjectMapper,
    private val ruleMapper: MenuSecurityRuleMapper,
    private val redisPublisher: MenuRuleRedisPublisher,
    private val permissionComputer: UserMenuPermissionComputer
) {

    private val log = LoggerFactory.getLogger(MenuSecurityRuleSyncConsumer::class.java)

    @KafkaListener(
        topics = ["menu.permission.changed"],
        groupId = "auth-service-menu-sync"
    )
    fun onMenuPermissionChanged(message: String) {
        log.info("Received menu permission change event: {}", message)

        try {
            val event = objectMapper.readValue(message, MenuPermissionChangedEvent::class.java)

            // Process event: upsert/soft-delete rules
            val modified = ruleMapper.processEvent(event)

            if (modified) {
                // Publish updated rules to Redis + notify all service instances
                redisPublisher.publishAllRules()
                log.info("Security rules synced for menuCode={}, action={}",
                    event.payload.menuCode, event.payload.action)

                // Async: recompute user permissions for affected roles
                val permCodes = event.payload.permissions.map { it.code }
                val roleIds = event.payload.assignedRoles.map { it.roleId }
                permissionComputer.recomputeForMenuChange(
                    menuCode = event.payload.menuCode,
                    permissions = permCodes,
                    affectedRoleIds = roleIds
                )
            }
        } catch (ex: Exception) {
            log.error("Failed to process menu permission change event: {}", ex.message, ex)
            // Don't rethrow — Kafka will retry via DLT if configured
        }
    }
}
