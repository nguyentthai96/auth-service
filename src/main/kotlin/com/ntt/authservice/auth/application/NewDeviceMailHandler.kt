package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.application.event.NewDeviceLoginEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Handles new device login events — enqueues notification email.
 * Uses AFTER_COMMIT to ensure login transaction succeeded before enqueuing.
 */
@Component
class NewDeviceMailHandler(
    private val mailQueueService: MailQueueService
) {

    private val log = LoggerFactory.getLogger(NewDeviceMailHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleNewDeviceLogin(event: NewDeviceLoginEvent) {
        val email = event.email
        if (email.isNullOrBlank()) {
            log.warn("Cannot send new device email — user {} has no email", event.userId)
            return
        }

        val templateData = mapOf(
            "userName" to event.username,
            "deviceName" to event.deviceName,
            "deviceType" to (event.deviceType ?: "Unknown"),
            "browserName" to (event.browserName ?: "Unknown"),
            "osName" to (event.osName ?: "Unknown"),
            "ipAddress" to (event.ipAddress ?: "Unknown"),
            "loginTime" to event.loginAt.toString()
        )

        try {
            mailQueueService.enqueue(
                recipient = email,
                templateCode = "NEW_DEVICE_LOGIN",
                templateData = templateData,
                createdBy = event.userId
            )
            log.info("NEW_DEVICE_MAIL enqueued for user={}, device={}", event.userId, event.deviceName)
        } catch (e: Exception) {
            // Fail-safe: mail enqueue failure should NOT propagate
            log.error("Failed to enqueue new device mail: userId={}, error={}", event.userId, e.message)
        }
    }
}
