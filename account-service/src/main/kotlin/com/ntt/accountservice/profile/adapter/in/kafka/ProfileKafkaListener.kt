package com.ntt.accountservice.profile.adapter.`in`.kafka

import com.ntt.accountservice.profile.application.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka listener for profile auto-creation on user registration/SSO provision (FR-005).
 * Listens to: iam.user.registered, iam.user.sso_provisioned
 */
@Component
class ProfileKafkaListener(
    private val profileService: ProfileService
) {

    private val log = LoggerFactory.getLogger(ProfileKafkaListener::class.java)

    /**
     * Handle user registration event — create default profile.
     */
    @KafkaListener(topics = ["iam.user.registered"], groupId = "account-profile-group")
    fun onUserRegistered(event: UserRegisteredEvent) {
        log.info("Received user.registered event: userId={}, username={}", event.userId, event.username)
        try {
            profileService.createDefaultProfile(event.userId, event.username, event.domainCode)
        } catch (e: Exception) {
            log.error("Failed to create default profile for userId={}: {}", event.userId, e.message, e)
        }
    }

    /**
     * Handle SSO provisioning event — create default profile for JIT-provisioned users.
     */
    @KafkaListener(topics = ["iam.user.sso_provisioned"], groupId = "account-profile-group")
    fun onSsoProvisioned(event: SsoProvisionedEvent) {
        log.info("Received sso_provisioned event: userId={}, provider={}", event.userId, event.provider)
        try {
            val displayName = event.email ?: "SSO User"
            profileService.createDefaultProfile(event.userId, displayName, event.domainCode)
        } catch (e: Exception) {
            log.error("Failed to create profile for SSO user userId={}: {}", event.userId, e.message, e)
        }
    }

    /** Event DTO for user registration (matches auth-service EventPublisher). */
    data class UserRegisteredEvent(
        val userId: Long,
        val username: String,
        val domainCode: String
    )

    /** Event DTO for SSO provisioning (matches auth-service SsoProvisionedEvent). */
    data class SsoProvisionedEvent(
        val userId: Long,
        val provider: String,
        val email: String?,
        val domainCode: String
    )
}
