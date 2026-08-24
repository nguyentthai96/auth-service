package com.ntt.authservice.auth.adapter.`in`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.RefreshTokenRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Kafka consumer for account status change events from account-service.
 *
 * Listens to topic: iam.account.status-changed
 * When user is deactivated in account-service:
 *   1. Updates users.status in auth DB → blocks new logins
 *   2. Revokes all active refresh tokens → blocks token refresh
 *
 * When user is reactivated:
 *   1. Updates users.status back to ACTIVE → allows logins again
 *
 * Event format:
 * ```json
 * {
 *   "userId": 12345,
 *   "newStatus": "INACTIVE",
 *   "reason": "admin_deactivation",
 *   "timestamp": 1234567890
 * }
 * ```
 */
@Component
@ConditionalOnProperty(name = ["spring.kafka.bootstrap-servers"])
class AccountStatusConsumer(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(AccountStatusConsumer::class.java)

    @KafkaListener(topics = ["iam.account.status-changed"], groupId = "auth-service")
    @Transactional
    fun onAccountStatusChanged(message: String) {
        log.info("Received account status change event: {}", message)

        try {
            val event = objectMapper.readTree(message)
            val userId = event["userId"]?.asLong()
                ?: throw IllegalArgumentException("Missing userId in event")
            val newStatus = event["newStatus"]?.asText()
                ?: throw IllegalArgumentException("Missing newStatus in event")
            val reason = event["reason"]?.asText() ?: "unknown"

            val user: UserEntity = userRepository.findById(userId).orElse(null)
            if (user == null) {
                log.warn("User not found in auth DB for status change: userId={}", userId)
                return
            }

            when (newStatus) {
                "INACTIVE", "SUSPENDED", "DELETED" -> {
                    // Deactivation flow: update status + revoke all tokens
                    user.status = newStatus
                    userRepository.save(user)

                    val revokedCount = refreshTokenRepository.revokeAllByUserId(userId)
                    log.info(
                        "Account deactivated in auth-service: userId={}, newStatus={}, reason={}, revokedTokens={}",
                        userId, newStatus, reason, revokedCount
                    )
                }

                "ACTIVE" -> {
                    // Reactivation flow: only update status (user needs to login again)
                    user.status = "ACTIVE"
                    userRepository.save(user)
                    log.info(
                        "Account reactivated in auth-service: userId={}, reason={}",
                        userId, reason
                    )
                }

                else -> {
                    log.warn("Unknown account status: {}, userId={}", newStatus, userId)
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to process account status change event: {}", ex.message, ex)
            // Don't rethrow — Kafka will retry via DLT if configured
        }
    }
}
