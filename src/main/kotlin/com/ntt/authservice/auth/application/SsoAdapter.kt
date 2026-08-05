package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserIdentityEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant

/**
 * SSO OAuth2 adapter — handles IdP callback, JIT provisioning, and identity linking.
 */
@Service
class SsoAdapter(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val userRepository: UserRepository,
    private val userIdentityRepository: UserIdentityRepository,
    private val domainRepository: DomainRepository,
    private val jwtService: JwtService,
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(SsoAdapter::class.java)
    private val restTemplate = RestTemplate()

    companion object {
        private const val SSO_ONLY_MARKER = "!SSO_ONLY!"
    }

    /**
     * Handle OAuth2 callback — exchange code for tokens, find/create user, issue internal JWT.
     */
    fun handleCallback(
        code: String,
        provider: String,
        redirectUri: String,
        authResponseBuilder: (Long) -> AuthResponse
    ): AuthResponse {
        // Exchange authorization code for IdP tokens
        val idpUser = exchangeCodeForUser(code, provider, redirectUri)

        // Look up existing identity
        val existingIdentity = userIdentityRepository.findByProviderAndProviderSub(provider, idpUser.sub)

        if (existingIdentity != null) {
            log.info("SSO login: existing identity found for provider={}, sub={}", provider, idpUser.sub)
            return authResponseBuilder(existingIdentity.userId)
        }

        // JIT provisioning
        if (!securityProperties.sso.autoProvisionEnabled) {
            throw SsoUserNotProvisionedException()
        }

        val user = provisionSsoUser(idpUser, provider)
        log.info("SSO JIT provisioned: userId={}, provider={}", user.id, provider)

        // Emit Kafka event
        kafkaTemplate.send("iam.user.sso_provisioned", user.id.toString(), provider)

        return authResponseBuilder(user.id)
    }

    /**
     * List available SSO providers.
     */
    fun getProviders(): List<SsoProviderInfo> {
        if (!securityProperties.sso.enabled) return emptyList()
        // Read from Spring OAuth2 client registrations would go here
        // For now, return configured providers
        return listOf(
            SsoProviderInfo("google", "Google", true),
            SsoProviderInfo("microsoft", "Microsoft", true),
            SsoProviderInfo("keycloak", "Keycloak", true)
        )
    }

    /**
     * Link an SSO identity to an existing user.
     */
    fun linkIdentity(userId: Long, code: String, provider: String, redirectUri: String): UserIdentityEntity {
        val idpUser = exchangeCodeForUser(code, provider, redirectUri)

        // Check for conflict
        val existing = userIdentityRepository.findByProviderAndProviderSub(provider, idpUser.sub)
        if (existing != null && existing.userId != userId) {
            throw SsoIdentityConflictException()
        }

        val identity = UserIdentityEntity().apply {
            this.userId = userId
            this.provider = provider
            this.providerSub = idpUser.sub
            this.providerEmail = idpUser.email
            this.providerName = idpUser.name
            this.linkedAt = Instant.now()
        }

        log.info("SSO identity linked: userId={}, provider={}", userId, provider)
        return userIdentityRepository.save(identity)
    }

    /**
     * Unlink an SSO identity from a user.
     */
    fun unlinkIdentity(userId: Long, provider: String) {
        val identity = userIdentityRepository.findByUserIdAndProviderAndActiveTrue(userId, provider)
            ?: throw ResourceNotFoundException("SSO Identity", "$userId:$provider")

        // Prevent unlinking last identity if user has no password
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }
        val identityCount = userIdentityRepository.countByUserIdAndActiveTrue(userId)
        if (identityCount <= 1 && user.passwordHash == SSO_ONLY_MARKER) {
            throw CannotUnlinkLastIdentityException()
        }

        identity.active = false
        userIdentityRepository.save(identity)
        log.info("SSO identity unlinked: userId={}, provider={}", userId, provider)
    }

    private fun provisionSsoUser(idpUser: IdpUserInfo, provider: String): UserEntity {
        val defaultDomain = domainRepository.findByCodeAndActiveTrue(securityProperties.sso.defaultDomainCode)

        val user = UserEntity().apply {
            this.username = idpUser.email ?: "${provider}_${idpUser.sub}"
            this.email = idpUser.email ?: ""
            this.passwordHash = SSO_ONLY_MARKER
            this.active = true
        }
        val savedUser = userRepository.save(user)

        // Create identity link
        val identity = UserIdentityEntity().apply {
            this.userId = savedUser.id
            this.provider = provider
            this.providerSub = idpUser.sub
            this.providerEmail = idpUser.email
            this.providerName = idpUser.name
            this.linkedAt = Instant.now()
        }
        userIdentityRepository.save(identity)

        return savedUser
    }

    /**
     * Exchange OAuth2 authorization code for user info.
     * TODO: Implement per-provider token exchange (Google, Microsoft, Keycloak)
     */
    private fun exchangeCodeForUser(code: String, provider: String, redirectUri: String): IdpUserInfo {
        // TODO: Call provider-specific token endpoint, parse id_token
        // This is a placeholder that should be replaced with actual OAuth2 token exchange
        try {
            log.info("Exchanging code with provider={}, redirectUri={}", provider, redirectUri)
            // Real implementation would:
            // 1. POST to provider's /token endpoint with code + client_secret
            // 2. Parse id_token JWT
            // 3. Extract sub, email, name from claims
            throw UnsupportedOperationException("OAuth2 token exchange not yet implemented for provider: $provider")
        } catch (e: Exception) {
            when (e) {
                is UnsupportedOperationException -> throw e
                else -> {
                    log.error("SSO token exchange failed for provider={}: {}", provider, e.message)
                    throw SsoTokenInvalidException()
                }
            }
        }
    }

    data class IdpUserInfo(val sub: String, val email: String?, val name: String?)
    data class SsoProviderInfo(val id: String, val name: String, val enabled: Boolean)
}
