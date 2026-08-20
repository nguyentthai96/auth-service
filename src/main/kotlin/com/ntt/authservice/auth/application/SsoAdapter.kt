package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.adapter.out.sso.OAuth2TokenExchanger
import com.ntt.authservice.auth.application.port.out.EventPublisher
import com.ntt.authservice.auth.application.port.out.SsoProvisionedEvent
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserIdentityEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * SSO OAuth2 adapter — handles IdP callback, JIT provisioning, and identity linking.
 */
@Service
class SsoAdapter(
    private val userRepository: UserRepository,
    private val userIdentityRepository: UserIdentityRepository,
    private val domainRepository: DomainRepository,
    private val jwtService: JwtService,
    private val securityProperties: SecurityProperties,
    private val oauth2TokenExchanger: OAuth2TokenExchanger,
    private val auditLogService: AuditLogService,
    private val eventPublisher: EventPublisher
) {

    private val log = LoggerFactory.getLogger(SsoAdapter::class.java)

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
            auditLogService.logEvent(existingIdentity.userId, AuditAction.SSO_LOGIN, "User", existingIdentity.userId.toString(), "provider=$provider, existingIdentity=true")
            return authResponseBuilder(existingIdentity.userId)
        }

        // JIT provisioning
        if (!securityProperties.sso.autoProvisionEnabled) {
            throw SsoUserNotProvisionedException()
        }

        val user = provisionSsoUser(idpUser, provider)
        log.info("SSO JIT provisioned: userId={}, provider={}", user.id, provider)
        auditLogService.logEvent(user.id, AuditAction.SSO_LOGIN, "User", user.id.toString(), "provider=$provider, jitProvisioned=true")

        // Publish SSO provisioning domain event (FR-007)
        eventPublisher.publish(SsoProvisionedEvent(
            userId = user.id!!,
            provider = provider,
            email = idpUser.email,
            domainCode = securityProperties.sso.defaultDomainCode
        ))

        return authResponseBuilder(user.id!!)
    }

    /**
     * List available SSO providers from configuration.
     * Reads from SecurityProperties.sso.providers — consistent with OAuth2TokenExchanger.
     */
    fun getProviders(): List<SsoProviderInfo> {
        if (!securityProperties.sso.enabled) return emptyList()
        return securityProperties.sso.providers
            .filter { (_, config) -> config.enabled }
            .map { (id, _) -> SsoProviderInfo(
                id = id,
                name = id.replaceFirstChar { it.uppercase() },
                enabled = true
            )}
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
        auditLogService.logEvent(userId, AuditAction.SSO_LINK, "User", userId.toString(), "provider=$provider")
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
        auditLogService.logEvent(userId, AuditAction.SSO_UNLINK, "User", userId.toString(), "provider=$provider")
    }

    private fun provisionSsoUser(idpUser: IdpUserInfo, provider: String): UserEntity {
        val defaultDomain = domainRepository.findByCodeAndActiveTrue(securityProperties.sso.defaultDomainCode)

        val user = UserEntity().apply {
            this.username = idpUser.email ?: "${provider}_${idpUser.sub}"
            this.email = idpUser.email ?: "${provider}_${idpUser.sub}@sso.local"
            this.passwordHash = SSO_ONLY_MARKER
            this.fullName = idpUser.name ?: idpUser.email ?: "${provider} User"
            this.status = "ACTIVE"
        }
        val savedUser = userRepository.save(user)

        // Create identity link
        val identity = UserIdentityEntity().apply {
            this.userId = savedUser.id!!
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
     * Delegates to OAuth2TokenExchanger which uses config-driven provider endpoints.
     */
    private fun exchangeCodeForUser(code: String, provider: String, redirectUri: String): IdpUserInfo {
        // Resolve client credentials from environment variables
        val clientId = System.getenv("${provider.uppercase()}_CLIENT_ID") ?: ""
        val clientSecret = System.getenv("${provider.uppercase()}_CLIENT_SECRET") ?: ""

        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw SsoTokenInvalidException("OAuth2 client credentials not configured for provider: $provider")
        }

        val result = oauth2TokenExchanger.exchange(provider, code, redirectUri, clientId, clientSecret)
        return IdpUserInfo(sub = result.sub, email = result.email, name = result.name)
    }

    data class IdpUserInfo(val sub: String, val email: String?, val name: String?)
    data class SsoProviderInfo(val id: String, val name: String, val enabled: Boolean)
}
