package com.ntt.authservice.auth.adapter.out.notification

import com.ntt.authservice.auth.application.port.out.NotificationGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Logging-based notification gateway — dev/test fallback.
 *
 * Active when: app.notification.provider=logging (default for dev profile).
 * Logs OTP codes and reset links to console for local testing without external dependencies.
 *
 * ⚠️ NEVER use in production — OTP codes visible in logs.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.notification",
    name = ["provider"],
    havingValue = "logging",
    matchIfMissing = true // Default: logging (safe for dev, must override in prod)
)
class LoggingNotificationGateway : NotificationGateway {

    private val log = LoggerFactory.getLogger(LoggingNotificationGateway::class.java)

    override fun sendOtp(userId: Long, channel: String, code: String, metadata: Map<String, String>) {
        log.info(
            "╔══════════════════════════════════════╗\n" +
            "║  [DEV] OTP CODE for userId={}       ║\n" +
            "║  Channel: {}                         ║\n" +
            "║  Code: {}                            ║\n" +
            "║  Metadata: {}                        ║\n" +
            "╚══════════════════════════════════════╝",
            userId, channel, code, metadata
        )
    }

    override fun sendPasswordResetLink(userId: Long, email: String, resetToken: String) {
        log.info(
            "╔══════════════════════════════════════╗\n" +
            "║  [DEV] PASSWORD RESET                ║\n" +
            "║  userId: {}                           ║\n" +
            "║  Email: {}                            ║\n" +
            "║  Token: {}                            ║\n" +
            "╚══════════════════════════════════════╝",
            userId, email, resetToken
        )
    }

    override fun sendNotification(userId: Long, type: String, channel: String, payload: Map<String, String>) {
        log.info(
            "[DEV] Notification: userId={}, type={}, channel={}, payload={}",
            userId, type, channel, payload
        )
    }
}
