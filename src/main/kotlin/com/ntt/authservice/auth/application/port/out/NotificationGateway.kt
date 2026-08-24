package com.ntt.authservice.auth.application.port.out

/**
 * Notification gateway port — abstraction for sending notifications (SMS, Email).
 *
 * Implementations:
 * - KafkaNotificationGateway: publishes to Kafka topic for downstream notification worker
 * - LoggingNotificationGateway: logs to console (dev/test, active when app.notification.provider=logging)
 *
 * Used by: MfaService (OTP dispatch), AuthController (password reset email).
 */
interface NotificationGateway {

    /**
     * Send OTP code to user via specified channel.
     *
     * @param userId Target user ID
     * @param channel Delivery channel: "sms" or "email"
     * @param code The OTP code to send
     * @param metadata Additional context (e.g., "ip" → login IP, "device" → user agent)
     */
    fun sendOtp(userId: Long, channel: String, code: String, metadata: Map<String, String> = emptyMap())

    /**
     * Send password reset link to user's email.
     *
     * @param userId Target user ID
     * @param email Recipient email address
     * @param resetToken Password reset token (one-time use)
     */
    fun sendPasswordResetLink(userId: Long, email: String, resetToken: String)

    /**
     * Send generic notification (extensible for future use cases).
     *
     * @param userId Target user ID
     * @param type Notification type (e.g., "ACCOUNT_LOCKED", "NEW_DEVICE_LOGIN")
     * @param channel Delivery channel
     * @param payload Notification-specific data
     */
    fun sendNotification(userId: Long, type: String, channel: String, payload: Map<String, String> = emptyMap())
}
