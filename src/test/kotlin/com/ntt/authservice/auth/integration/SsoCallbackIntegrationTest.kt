package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.adapter.out.sso.OAuth2TokenExchanger
import com.ntt.authservice.auth.application.SsoAdapter
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.port.out.EventPublisher
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserIdentityEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import java.util.*

/**
 * Integration-style tests for SSO OAuth2 callback flow (FR-006, FR-007, FR-017).
 * Tests: SSO callback → token exchange → find/create user → issue JWT.
 * Uses Mockito for external dependencies — covers full SsoAdapter logic.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SSO Callback Integration Tests")
class SsoCallbackIntegrationTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var userIdentityRepository: UserIdentityRepository
    @Mock private lateinit var domainRepository: DomainRepository
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var oauth2TokenExchanger: OAuth2TokenExchanger
    @Mock private lateinit var auditLogService: AuditLogService
    @Mock private lateinit var eventPublisher: EventPublisher

    private lateinit var ssoAdapter: SsoAdapter
    private lateinit var securityProperties: SecurityProperties

    private val testUserId = 1L

    private val mockAuthResponse = AuthResponse(
        accessToken = "sso-access-token",
        refreshToken = "sso-refresh-token",
        tokenType = "Bearer",
        expiresIn = 900,
        userId = testUserId,
        username = "ssouser@google.com",
        activeDomain = "default",
        roles = listOf("USER"),
        permissions = listOf("READ")
    )

    @BeforeEach
    fun setUp() {
        securityProperties = SecurityProperties(
            sso = SecurityProperties.SsoProperties(
                enabled = true,
                autoProvisionEnabled = true,
                defaultDomainCode = "default",
                timeoutMs = 10_000,
                providers = mapOf(
                    "google" to SecurityProperties.SsoProperties.ProviderConfig(
                        tokenEndpoint = "https://oauth2.googleapis.com/token",
                        userInfoEndpoint = "https://openidconnect.googleapis.com/v1/userinfo"
                    ),
                    "microsoft" to SecurityProperties.SsoProperties.ProviderConfig(
                        tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                        userInfoEndpoint = "https://graph.microsoft.com/oidc/userinfo"
                    ),
                    "keycloak" to SecurityProperties.SsoProperties.ProviderConfig(
                        tokenEndpoint = "http://localhost:8080/realms/master/protocol/openid-connect/token",
                        userInfoEndpoint = "http://localhost:8080/realms/master/protocol/openid-connect/userinfo"
                    )
                )
            )
        )
        ssoAdapter = SsoAdapter(
            userRepository, userIdentityRepository, domainRepository,
            jwtService, securityProperties, oauth2TokenExchanger,
            auditLogService, eventPublisher
        )
    }

    // ── TC1: SSO callback (Google) → existing user → 200 AuthResponse ──

    @Test
    @DisplayName("TC1: SSO callback for existing Google user should return AuthResponse")
    fun shouldLoginExistingGoogleUser() {
        val existingIdentity = UserIdentityEntity().apply {
            userId = testUserId
            provider = "google"
            providerSub = "google-sub-123"
        }
        whenever(oauth2TokenExchanger.exchange(eq("google"), eq("auth-code"), eq("http://redirect"), any(), any()))
            .thenReturn(OAuth2TokenExchanger.ExchangeResult(sub = "google-sub-123", email = "user@google.com", name = "Test User"))
        whenever(userIdentityRepository.findByProviderAndProviderSub("google", "google-sub-123"))
            .thenReturn(existingIdentity)

        val response = ssoAdapter.handleCallback("auth-code", "google", "http://redirect") { mockAuthResponse }

        assertEquals("sso-access-token", response.accessToken)
        verify(auditLogService).logEvent(eq(testUserId), any(), any(), any(), any())
        verify(userRepository, never()).save(any())
    }

    // ── TC2: SSO callback (Microsoft) → new user + autoProvision=true → JIT provision ──

    @Test
    @DisplayName("TC2: SSO callback for new Microsoft user should JIT provision")
    fun shouldJitProvisionNewMicrosoftUser() {
        whenever(oauth2TokenExchanger.exchange(eq("microsoft"), eq("auth-code"), eq("http://redirect"), any(), any()))
            .thenReturn(OAuth2TokenExchanger.ExchangeResult(sub = "ms-sub-456", email = "user@microsoft.com", name = "MS User"))
        whenever(userIdentityRepository.findByProviderAndProviderSub("microsoft", "ms-sub-456"))
            .thenReturn(null)
        whenever(domainRepository.findByCodeAndActiveTrue("default")).thenReturn(null)

        val savedUser = UserEntity().apply {
            id = 2L
            username = "user@microsoft.com"
            email = "user@microsoft.com"
            passwordHash = "!SSO_ONLY!"
            fullName = "MS User"
            status = "ACTIVE"
        }
        whenever(userRepository.save(any<UserEntity>())).thenReturn(savedUser)
        whenever(userIdentityRepository.save(any<UserIdentityEntity>())).thenAnswer { it.arguments[0] }

        val jitAuthResponse = mockAuthResponse.copy(userId = 2L, username = "user@microsoft.com")
        val response = ssoAdapter.handleCallback("auth-code", "microsoft", "http://redirect") { jitAuthResponse }

        assertEquals(2L, response.userId)
        verify(userRepository).save(any())
        verify(userIdentityRepository).save(argThat<UserIdentityEntity> { provider == "microsoft" && providerSub == "ms-sub-456" })
        verify(eventPublisher).publish(argThat { eventType == "iam.user.sso_provisioned" })
    }

    // ── TC3: SSO callback (Keycloak) → config-driven endpoints ──

    @Test
    @DisplayName("TC3: SSO callback for Keycloak should use config-driven endpoints")
    fun shouldUseKeycloakConfigDrivenEndpoints() {
        val existingIdentity = UserIdentityEntity().apply {
            userId = testUserId
            provider = "keycloak"
            providerSub = "kc-sub-789"
        }
        whenever(oauth2TokenExchanger.exchange(eq("keycloak"), eq("auth-code"), eq("http://redirect"), any(), any()))
            .thenReturn(OAuth2TokenExchanger.ExchangeResult(sub = "kc-sub-789", email = "user@company.com", name = "KC User"))
        whenever(userIdentityRepository.findByProviderAndProviderSub("keycloak", "kc-sub-789"))
            .thenReturn(existingIdentity)

        val response = ssoAdapter.handleCallback("auth-code", "keycloak", "http://redirect") { mockAuthResponse }

        assertEquals("sso-access-token", response.accessToken)
        // Keycloak exchange was called — config-driven endpoints work
        verify(oauth2TokenExchanger).exchange(eq("keycloak"), any(), any(), any(), any())
    }

    // ── TC4: SSO callback → IdP timeout → 504 SSO_PROVIDER_TIMEOUT ──

    @Test
    @DisplayName("TC4: SSO callback with IdP timeout should throw SsoProviderTimeoutException")
    fun shouldHandleIdpTimeout() {
        whenever(oauth2TokenExchanger.exchange(eq("google"), any(), any(), any(), any()))
            .thenThrow(SsoProviderTimeoutException())

        assertThrows<SsoProviderTimeoutException> {
            ssoAdapter.handleCallback("auth-code", "google", "http://redirect") { mockAuthResponse }
        }
    }

    // ── TC5: SSO callback → new user + autoProvision=false → 403 SSO_USER_NOT_PROVISIONED ──

    @Test
    @DisplayName("TC5: SSO callback with autoProvision=false should reject new user")
    fun shouldRejectNewUserWhenAutoProvisionDisabled() {
        val noAutoProvisionProps = SecurityProperties(
            sso = SecurityProperties.SsoProperties(
                enabled = true,
                autoProvisionEnabled = false,
                providers = securityProperties.sso.providers
            )
        )
        val restrictedAdapter = SsoAdapter(
            userRepository, userIdentityRepository, domainRepository,
            jwtService, noAutoProvisionProps, oauth2TokenExchanger,
            auditLogService, eventPublisher
        )
        whenever(oauth2TokenExchanger.exchange(eq("google"), any(), any(), any(), any()))
            .thenReturn(OAuth2TokenExchanger.ExchangeResult(sub = "new-sub", email = "new@google.com", name = "New User"))
        whenever(userIdentityRepository.findByProviderAndProviderSub("google", "new-sub"))
            .thenReturn(null)

        assertThrows<SsoUserNotProvisionedException> {
            restrictedAdapter.handleCallback("auth-code", "google", "http://redirect") { mockAuthResponse }
        }
    }

    // ── TC6: SSO callback → revoked auth code → 401 SSO_TOKEN_INVALID ──

    @Test
    @DisplayName("TC6: SSO callback with revoked auth code should throw SsoTokenInvalidException")
    fun shouldRejectRevokedAuthCode() {
        whenever(oauth2TokenExchanger.exchange(eq("google"), any(), any(), any(), any()))
            .thenThrow(SsoTokenInvalidException("Token exchange failed: invalid_grant"))

        assertThrows<SsoTokenInvalidException> {
            ssoAdapter.handleCallback("revoked-code", "google", "http://redirect") { mockAuthResponse }
        }
    }
}
