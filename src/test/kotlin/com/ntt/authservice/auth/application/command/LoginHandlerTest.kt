package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.*
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AccountLockedException
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Unit tests for LoginHandler — mocks all ports, tests handler logic.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("LoginHandler Tests")
class LoginHandlerTest {

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
        username = "testuser",
        email = Email("test@example.com"),
        passwordHash = PasswordHash("\$2a\$10\$encodedHash"),
        fullName = "Test User",
        status = UserStatus.Active
    )

    private val testAuthToken = AuthToken(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        tokenType = "Bearer",
        expiresIn = 1800,
        userId = 1L,
        username = "testuser",
        activeDomain = "default",
        roles = listOf("ADMIN"),
        permissions = listOf("user:read", "user:write")
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

    @Test
    @DisplayName("Should return Success when credentials are valid")
    fun shouldLoginSuccessfully() {
        // Given
        whenever(userPort.findByUsernameAndActive("testuser")).thenReturn(testUser)
        whenever(securityProperties.password).thenReturn(passwordConfig)
        whenever(securityProperties.mfa).thenReturn(mfaConfig)
        whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
        whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
        whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
        whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
        whenever(tokenGenerator.generateAuthResponse(any(), any())).thenReturn(testAuthToken)

        val command = LoginCommand(username = "testuser", password = "password123")

        // When
        val result = runBlocking { handler.handle(command) }

        // Then
        assertTrue(result is LoginResult.Success)
        assertEquals("test-access-token", (result as LoginResult.Success).response.accessToken)
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when user not found")
    fun shouldThrowWhenUserNotFound() {
        // Given
        whenever(userPort.findByUsernameAndActive("unknown")).thenReturn(null)

        val command = LoginCommand(username = "unknown", password = "password123")

        // When/Then
        assertThrows<InvalidCredentialsException> { runBlocking { handler.handle(command) } }
    }

    @Test
    @DisplayName("Should throw AccountLockedException when user is locked")
    fun shouldThrowWhenUserLocked() {
        // Given
        val lockedUser = User(
            id = UserId(1L),
            username = "lockeduser",
            email = Email("locked@example.com"),
            passwordHash = PasswordHash("\$2a\$10\$encodedHash"),
            fullName = "Locked User",
            status = UserStatus.Locked(Instant.now().plusSeconds(3600), "Too many failed attempts")
        )
        whenever(userPort.findByUsernameAndActive("lockeduser")).thenReturn(lockedUser)

        val command = LoginCommand(username = "lockeduser", password = "password123")

        // When/Then
        assertThrows<AccountLockedException> { runBlocking { handler.handle(command) } }
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when password wrong")
    fun shouldThrowWhenPasswordWrong() {
        // Given
        whenever(userPort.findByUsernameAndActive("testuser")).thenReturn(testUser)
        whenever(securityProperties.password).thenReturn(passwordConfig)
        whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
        whenever(passwordConfig.lockDurationMinutes).thenReturn(15)
        whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(false)
        whenever(userPort.save(any())).thenReturn(testUser)

        val command = LoginCommand(username = "testuser", password = "wrongpassword")

        // When/Then
        assertThrows<InvalidCredentialsException> { runBlocking { handler.handle(command) } }
    }
}
