package com.ntt.authservice.auth.adapter.`in`.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.application.PasswordPolicyConfigProvider
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SystemConfigEventListener(
    private val passwordPolicyConfigProvider: PasswordPolicyConfigProvider,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SystemConfigEventListener::class.java)

    @KafkaListener(topics = ["system.config.changed"], groupId = "auth-service-config-group")
    fun handleConfigChanged(payload: String) {
        try {
            val event = objectMapper.readTree(payload)
            val key = event.path("key").asText()
            
            log.info("Received config change event for key: {}", key)
            
            if (key.startsWith("password.")) {
                passwordPolicyConfigProvider.invalidateCache()
            }
        } catch (ex: Exception) {
            log.error("Failed to process system.config.changed event: {}", payload, ex)
        }
    }
}
