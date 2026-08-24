package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.application.*
import com.ntt.authservice.auth.application.command.LoginCommand
import com.ntt.authservice.auth.application.command.LoginHandler
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.PasswordExpiredException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Tests for password expiry enforcement during login flow.
 * Verifies that expired passwords block login with AUTH_018.
 *
 * FR-013: Password policy enforcement during login
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("Password Expiry Login Tests")
class PasswordExpiryLoginTest {

    @Mock private lateinit var userPort: UserPort
    @Mock private lateinit var domainPort: DomainPort
    @Mock private lateinit var tokenStore: TokenStore
    @Mock private lateinit var captchaGateway: CaptchaGateway
    @Mock private lateinit var getUserRolesHandler: GetUserRolesHandler
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var tokenGenerator: TokenGenerator
    @Mock private lateinit var passwordConfig: SecurityProperties.PasswordProperties
    @Mock private lateinit var mfaConfig: SecurityProperties.MfaProperties
    @Mock private lateinit var passwordPolicyService: PasswordPolicyService
    @Mock private lateinit var loginRateLimitService: LoginRateLimitService
    @Mock private lateinit var sessionPolicyService: SessionPolicyService
    @Mock private lateinit var loginSessionService: LoginSessionService
    @Mock private lateinit var sessionPromotionService: SessionPromotionService

    private lateinit var handler: LoginHandler

    private val testUser = User(
        id = UserId(1L),
        username = "expiredpwd-user",
        email = Email("expiredpwd@example.com"),
        passwordHash = PasswordHash("\$2a\$10\$encodedHash"),
        fullName = "Expired Password User",
        status = UserStatus.Active
    )

    private val testAuthToken = AuthToken(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        tokenType = "Bearer",
        expiresIn = 1800,
        userId = 1L,
        username = "expiredpwd-user",
        activeDomain = "default",
        roles = listOf("USER"),
        permissions = listOf("read")
    )

    @BeforeEach
    fun setUp() {
        handler = LoginHandler(
            userPort = userPort,
            domainPort = domainPort,
            tokenStore = tokenStore,
            captchaGateway = captchaGateway,
            getUserRolesHandler = getUserRolesHandler,
            securityProperties = securityProperties,
            tokenGenerator = tokenGenerator,
            passwordPolicyService = passwordPolicyService,
            loginRateLimitService = loginRateLimitService,
            sessionPolicyService = sessionPolicyService,
            loginSessionService = loginSessionService,
            sessionPromotionService = sessionPromotionService
        )
    }

    private fun setupBasicMocks() {
        whenever(userPort.findByUsernameAndActive("expiredpwd-user")).thenReturn(testUser)
        whenever(securityProperties.password).thenReturn(passwordConfig)
        whenever(securityProperties.mfa).thenReturn(mfaConfig)
        whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
        whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
        whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
        whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
    }

    @Test
    @DisplayName("TC1: Login with expired password → throws PasswordExpiredException (AUTH_018)")
    fun shouldThrowPasswordExpiredOnExpiredPassword() {
        // Given
        setupBasicMocks()
        val domainInfo = object {
            val id = 100L
            val code = "default"
        }
        whenever(domainPort.findByCodeAndActive("default")).thenReturn(domainInfo)
        whenever(passwordPolicyService.isPasswordExpired(1L, 100L)).thenReturn(true)

        val command = LoginCommand(username = "expiredpwd-user", password = "password123")

        // When/Then
        val exception = assertThrows<PasswordExpiredException> {
            runBlocking { handler.handle(command) }
        }
        assertTrue(exception.message!!.contains("expired"))
    }

    @Test
    @DisplayName("TC2: Login with non-expired password → Success")
    fun shouldSucceedWithNonExpiredPassword() {
        // Given
        setupBasicMocks()
        val domainInfo = object {
            val id = 100L
            val code = "default"
        }
        whenever(domainPort.findByCodeAndActive("default")).thenReturn(domainInfo)
        whenever(passwordPolicyService.isPasswordExpired(1L, 100L)).thenReturn(false)
        whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)
        whenever(getUserRolesHandler.handle(any())).thenReturn(listOf("USER"))

        val command = LoginCommand(username = "expiredpwd-user", password = "password123")

        // When
        val result = runBlocking { handler.handle(command) }

        // Then
        assertTrue(result is LoginResult.Success)
    }

    @Test
    @DisplayName("TC3: Password expiry check uses domain from request when provided")
    fun shouldUseDomainFromRequest() {
        // Given
        setupBasicMocks()
        val specificDomain = object {
            val id = 200L
            val code = "corp"
        }
        whenever(domainPort.findByCodeAndActive("corp")).thenReturn(specificDomain)
        whenever(passwordPolicyService.isPasswordExpired(1L, 200L)).thenReturn(true)

        val command = LoginCommand(
            username = "expiredpwd-user", password = "password123",
            domainCode = "corp"
        )

        // When/Then
        assertThrows<PasswordExpiredException> {
            runBlocking { handler.handle(command) }
        }
        verify(passwordPolicyService).isPasswordExpired(1L, 200L)
    }
}
