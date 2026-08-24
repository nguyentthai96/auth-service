package com.ntt.authservice.auth.adapter.out.notification

import com.ntt.authservice.auth.application.port.out.EventPublisher
import com.ntt.authservice.auth.application.port.out.NotificationGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Kafka-based notification gateway — production implementation.
 *
 * Active when: app.notification.provider=kafka.
 * Publishes notification requests to Kafka topic `notification.send`
 * for downstream notification worker to process (SMS gateway, Email provider).
 *
 * Message format (JSON):
 * ```json
 * {
 *   "type": "OTP|PASSWORD_RESET|GENERIC",
 *   "userId": 123,
 *   "channel": "sms|email",
 *   "payload": { "code": "123456", "email": "user@example.com", ... },
 *   "metadata": { "ip": "1.2.3.4", ... },
 *   "timestamp": 1234567890
 * }
 * ```
 */
@Component
@ConditionalOnProperty(
    prefix = "app.notification",
    name = ["provider"],
    havingValue = "kafka"
)
class KafkaNotificationGateway(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : NotificationGateway {

    private val log = LoggerFactory.getLogger(KafkaNotificationGateway::class.java)

    companion object {
        private const val TOPIC = "notification.send"
    }

    override fun sendOtp(userId: Long, channel: String, code: String, metadata: Map<String, String>) {
        val message = mapOf(
            "type" to "OTP",
            "userId" to userId,
            "channel" to channel,
            "payload" to mapOf("code" to code),
            "metadata" to metadata,
            "timestamp" to System.currentTimeMillis()
        )

        publishToKafka(userId.toString(), message)
        log.info("OTP notification published for userId={}, channel={}", userId, channel)
    }

    override fun sendPasswordResetLink(userId: Long, email: String, resetToken: String) {
        val message = mapOf(
            "type" to "PASSWORD_RESET",
            "userId" to userId,
            "channel" to "email",
            "payload" to mapOf(
                "email" to email,
                "resetToken" to resetToken
            ),
            "metadata" to emptyMap<String, String>(),
            "timestamp" to System.currentTimeMillis()
        )

        publishToKafka(userId.toString(), message)
        log.info("Password reset notification published for userId={}, email={}", userId, email)
    }

    override fun sendNotification(userId: Long, type: String, channel: String, payload: Map<String, String>) {
        val message = mapOf(
            "type" to type,
            "userId" to userId,
            "channel" to channel,
            "payload" to payload,
            "metadata" to emptyMap<String, String>(),
            "timestamp" to System.currentTimeMillis()
        )

        publishToKafka(userId.toString(), message)
        log.info("Notification published for userId={}, type={}, channel={}", userId, type, channel)
    }

    private fun publishToKafka(key: String, message: Map<String, Any>) {
        try {
            kafkaTemplate.send(TOPIC, key, message)
        } catch (e: Exception) {
            log.error("Failed to publish notification to Kafka: {}", e.message, e)
            // Fail-safe: notification failure should not break auth flow
        }
    }
}
