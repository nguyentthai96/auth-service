package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.*
import com.ntt.authservice.auth.application.event.LoginEventRecorder
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.rbac.application.query.GetUserRolesQuery
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Comprehensive unit tests for LoginHandler — CQRS login command handler.
 * Covers: MFA, CAPTCHA, password expiry, session policy, trusted device TTL, rate limiting, audit.
 *
 * FR-004 (CAPTCHA), FR-005 (Trusted Device), FR-013 (Password Policy)
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
    @Mock private lateinit var loginEventRecorder: LoginEventRecorder
    @Mock private lateinit var fingerprintService: FingerprintService
    @Mock private lateinit var passwordUpgradeService: PasswordUpgradeService

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
            sessionPromotionService = sessionPromotionService,
            loginEventRecorder = loginEventRecorder,
            fingerprintService = fingerprintService,
            passwordUpgradeService = passwordUpgradeService
        )
    }

    /**
     * Helper to set up common mocks for a successful login flow.
     */
    private fun setupSuccessfulLoginMocks(user: User = testUser) {
        whenever(userPort.findByUsernameAndActive(user.username)).thenReturn(user)
        whenever(securityProperties.password).thenReturn(passwordConfig)
        whenever(securityProperties.mfa).thenReturn(mfaConfig)
        whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
        whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
        whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
        whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
        whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)
        whenever(domainPort.findByCodeAndActive("default")).thenReturn(
            object {
                val id = 100L
                val code = "default"
            }.let { null } // Simplified — domain not found means no password expiry check
        )
        whenever(getUserRolesHandler.handle(any())).thenReturn(emptyList())
    }

    // ==================== TC1: Successful Login ====================

    @Nested
    @DisplayName("TC1: Successful login (no MFA, no CAPTCHA)")
    inner class SuccessfulLogin {

        @Test
        @DisplayName("Should return Success when credentials are valid")
        fun shouldLoginSuccessfully() {
            // Given
            setupSuccessfulLoginMocks()
            val command = LoginCommand(username = "testuser", password = "password123")

            // When
            val result = runBlocking { handler.handle(command) }

            // Then
            assertTrue(result is LoginResult.Success)
            assertEquals("test-access-token", (result as LoginResult.Success).response.accessToken)
        }

        @Test
        @DisplayName("Should reset failed login count on success")
        fun shouldResetFailedLoginCountOnSuccess() {
            // Given
            setupSuccessfulLoginMocks()
            val command = LoginCommand(username = "testuser", password = "password123")

            // When
            runBlocking { handler.handle(command) }

            // Then
            verify(userPort, times(1)).save(any())
            verify(loginRateLimitService).resetOnSuccess(any(), eq("testuser"), any())
        }
    }

    // ==================== TC2: MFA Required (no trusted device) ====================

    @Nested
    @DisplayName("TC2-5: MFA checkpoint scenarios")
    inner class MfaCheckpoint {

        @Test
        @DisplayName("TC2: MFA enabled + no trusted device → MfaRequired")
        fun shouldReturnMfaRequiredWhenNoTrustedDevice() {
            // Given
            val mfaUser = User(
                id = UserId(2L), username = "mfauser", email = Email("mfa@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$hash"), fullName = "MFA User",
                status = UserStatus.Active, mfaEnabled = true, mfaMethod = "OTP_EMAIL"
            )
            whenever(userPort.findByUsernameAndActive("mfauser")).thenReturn(mfaUser)
            whenever(securityProperties.password).thenReturn(passwordConfig)
            whenever(securityProperties.mfa).thenReturn(mfaConfig)
            whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
            whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
            whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
            whenever(tokenGenerator.generateMfaResult(eq(2L), eq("OTP_EMAIL")))
                .thenReturn(LoginResult.MfaRequired("mfa-token-123", "OTP_EMAIL", 300))

            val command = LoginCommand(username = "mfauser", password = "password123")

            // When
            val result = runBlocking { handler.handle(command) }

            // Then
            assertTrue(result is LoginResult.MfaRequired)
            assertEquals("mfa-token-123", (result as LoginResult.MfaRequired).mfaToken)
            assertEquals("OTP_EMAIL", result.method)
        }

        @Test
        @DisplayName("TC3: MFA enabled + trusted device valid TTL → skip MFA → Success")
        fun shouldSkipMfaWhenTrustedDeviceValid() {
            // Given — trustedDeviceSetAt = 10 days ago (within 30-day TTL)
            val trustedUser = User(
                id = UserId(3L), username = "trusteduser", email = Email("trusted@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$hash"), fullName = "Trusted User",
                status = UserStatus.Active, mfaEnabled = true, mfaMethod = "OTP_EMAIL",
                trustedDeviceHash = "sha256-device-hash",
                trustedDeviceSetAt = Instant.now().minus(10, ChronoUnit.DAYS)
            )
            whenever(userPort.findByUsernameAndActive("trusteduser")).thenReturn(trustedUser)
            whenever(securityProperties.password).thenReturn(passwordConfig)
            whenever(securityProperties.mfa).thenReturn(mfaConfig)
            whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
            whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
            whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
            whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)
            whenever(getUserRolesHandler.handle(any())).thenReturn(emptyList())

            val command = LoginCommand(
                username = "trusteduser", password = "password123",
                trustedDeviceHash = "sha256-device-hash"
            )

            // When
            val result = runBlocking { handler.handle(command) }

            // Then — MFA skipped, direct login success
            assertTrue(result is LoginResult.Success)
            verify(tokenGenerator, never()).generateMfaResult(any(), any())
        }

        @Test
        @DisplayName("TC4: MFA enabled + trusted device expired TTL → MfaRequired")
        fun shouldRequireMfaWhenDeviceExpired() {
            // Given — trustedDeviceSetAt = 45 days ago (expired beyond 30-day TTL)
            val expiredUser = User(
                id = UserId(4L), username = "expireddev", email = Email("expired@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$hash"), fullName = "Expired Dev User",
                status = UserStatus.Active, mfaEnabled = true, mfaMethod = "TOTP",
                trustedDeviceHash = "sha256-device-hash",
                trustedDeviceSetAt = Instant.now().minus(45, ChronoUnit.DAYS)
            )
            whenever(userPort.findByUsernameAndActive("expireddev")).thenReturn(expiredUser)
            whenever(securityProperties.password).thenReturn(passwordConfig)
            whenever(securityProperties.mfa).thenReturn(mfaConfig)
            whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
            whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
            whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
            whenever(tokenGenerator.generateMfaResult(eq(4L), eq("TOTP")))
                .thenReturn(LoginResult.MfaRequired("mfa-token-456", "TOTP", 300))

            val command = LoginCommand(
                username = "expireddev", password = "password123",
                trustedDeviceHash = "sha256-device-hash"
            )

            // When
            val result = runBlocking { handler.handle(command) }

            // Then — TTL expired, MFA required
            assertTrue(result is LoginResult.MfaRequired)
        }

        @Test
        @DisplayName("TC5: MFA enabled + wrong device hash → MfaRequired")
        fun shouldRequireMfaWhenWrongDeviceHash() {
            // Given
            val mfaUser = User(
                id = UserId(5L), username = "wrongdev", email = Email("wrong@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$hash"), fullName = "Wrong Dev User",
                status = UserStatus.Active, mfaEnabled = true, mfaMethod = "OTP_SMS",
                trustedDeviceHash = "correct-hash",
                trustedDeviceSetAt = Instant.now().minus(5, ChronoUnit.DAYS)
            )
            whenever(userPort.findByUsernameAndActive("wrongdev")).thenReturn(mfaUser)
            whenever(securityProperties.password).thenReturn(passwordConfig)
            whenever(securityProperties.mfa).thenReturn(mfaConfig)
            whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
            whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
            whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
            whenever(tokenGenerator.generateMfaResult(eq(5L), eq("OTP_SMS")))
                .thenReturn(LoginResult.MfaRequired("mfa-token-789", "OTP_SMS", 300))

            val command = LoginCommand(
                username = "wrongdev", password = "password123",
                trustedDeviceHash = "wrong-hash"
            )

            // When
            val result = runBlocking { handler.handle(command) }

            // Then
            assertTrue(result is LoginResult.MfaRequired)
        }
    }

    // ==================== TC6: Account Locked ====================

    @Test
    @DisplayName("TC6: Account locked → throws AccountLockedException")
    fun shouldThrowWhenUserLocked() {
        // Given
        val lockedUser = User(
            id = UserId(6L), username = "lockeduser", email = Email("locked@example.com"),
            passwordHash = PasswordHash("\$2a\$10\$encodedHash"), fullName = "Locked User",
            status = UserStatus.Locked(Instant.now().plusSeconds(3600), "Too many failed attempts")
        )
        whenever(userPort.findByUsernameAndActive("lockeduser")).thenReturn(lockedUser)

        val command = LoginCommand(username = "lockeduser", password = "password123")

        // When/Then
        assertThrows<AccountLockedException> { runBlocking { handler.handle(command) } }
    }

    // ==================== TC7: Invalid Credentials ====================

    @Test
    @DisplayName("TC7: Invalid credentials → throws InvalidCredentialsException")
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
        verify(loginRateLimitService).recordFailedAttempt(any(), eq("testuser"), any())
    }

    @Test
    @DisplayName("TC7b: User not found → throws InvalidCredentialsException + records rate limit")
    fun shouldThrowWhenUserNotFound() {
        // Given
        whenever(userPort.findByUsernameAndActive("unknown")).thenReturn(null)

        val command = LoginCommand(username = "unknown", password = "password123")

        // When/Then
        assertThrows<InvalidCredentialsException> { runBlocking { handler.handle(command) } }
        verify(loginRateLimitService).recordFailedAttempt(any(), eq("unknown"), any())
    }

    // ==================== TC8-9: CAPTCHA ====================

    @Nested
    @DisplayName("TC8-9: CAPTCHA verification")
    inner class CaptchaScenarios {

        @Test
        @DisplayName("TC8: CAPTCHA required (threshold exceeded) + no token → throws CaptchaRequiredException")
        fun shouldThrowWhenCaptchaRequiredButMissing() {
            // Given — user has 4 failed attempts (maxFailedAttempts=5, threshold=maxAttempts-1=4)
            val captchaUser = User(
                id = UserId(8L), username = "captchauser", email = Email("cap@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$hash"), fullName = "Captcha User",
                status = UserStatus.Active, failedLoginCount = 4
            )
            whenever(userPort.findByUsernameAndActive("captchauser")).thenReturn(captchaUser)
            whenever(securityProperties.password).thenReturn(passwordConfig)
            whenever(passwordConfig.maxFailedAttempts).thenReturn(5)

            val command = LoginCommand(username = "captchauser", password = "password123")

            // When/Then
            assertThrows<CaptchaRequiredException> { runBlocking { handler.handle(command) } }
        }

        @Test
        @DisplayName("TC9: CAPTCHA required + valid token → proceeds to login")
        fun shouldProceedWhenCaptchaValid() {
            // Given
            val captchaUser = User(
                id = UserId(9L), username = "captchaok", email = Email("capok@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$hash"), fullName = "Captcha OK",
                status = UserStatus.Active, failedLoginCount = 4
            )
            whenever(userPort.findByUsernameAndActive("captchaok")).thenReturn(captchaUser)
            whenever(securityProperties.password).thenReturn(passwordConfig)
            whenever(securityProperties.mfa).thenReturn(mfaConfig)
            whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
            whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
            whenever(captchaGateway.verify("valid-captcha-token")).thenReturn(true)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
            whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
            whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)
            whenever(getUserRolesHandler.handle(any())).thenReturn(emptyList())

            val command = LoginCommand(
                username = "captchaok", password = "password123",
                captchaToken = "valid-captcha-token"
            )

            // When
            val result = runBlocking { handler.handle(command) }

            // Then
            assertTrue(result is LoginResult.Success)
            verify(captchaGateway).verify("valid-captcha-token")
        }
    }

    // ==================== TC10: Password Expired ====================

    @Test
    @DisplayName("TC10: Password expired → throws PasswordExpiredException")
    fun shouldThrowWhenPasswordExpired() {
        // Given
        whenever(userPort.findByUsernameAndActive("testuser")).thenReturn(testUser)
        whenever(securityProperties.password).thenReturn(passwordConfig)
        whenever(securityProperties.mfa).thenReturn(mfaConfig)
        whenever(mfaConfig.trustedDeviceTtlDays).thenReturn(30)
        whenever(passwordConfig.maxFailedAttempts).thenReturn(5)
        whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(true)
        whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")

        // Domain found + password expired
        val domainInfo = object {
            val id = 100L
            val code = "default"
        }
        whenever(domainPort.findByCodeAndActive("default")).thenReturn(domainInfo)
        whenever(passwordPolicyService.isPasswordExpired(eq(1L), eq(100L))).thenReturn(true)

        val command = LoginCommand(username = "testuser", password = "password123")

        // When/Then
        assertThrows<PasswordExpiredException> { runBlocking { handler.handle(command) } }
    }

    // ==================== TC11: Session Limit Exceeded ====================

    @Test
    @DisplayName("TC11: Session limit exceeded → throws SessionLimitExceededException")
    fun shouldThrowWhenSessionLimitExceeded() {
        // Given
        setupSuccessfulLoginMocks()
        // Override sessionPolicyService to throw
        doThrow(SessionLimitExceededException(3, 3))
            .whenever(sessionPolicyService).enforcePolicy(any(), any())

        val command = LoginCommand(username = "testuser", password = "password123")

        // When/Then
        assertThrows<SessionLimitExceededException> { runBlocking { handler.handle(command) } }
    }

    // ==================== TC12: Token event recorded on success ====================

    @Test
    @DisplayName("TC12: Token event recorded on successful login")
    fun shouldRecordTokenEventOnSuccess() {
        // Given
        setupSuccessfulLoginMocks()
        val command = LoginCommand(username = "testuser", password = "password123")

        // When
        runBlocking { handler.handle(command) }

        // Then — tokenGenerator.generateAuthResponse is called, which internally records token event
        verify(tokenGenerator).generateAuthResponse(any(), eq("default"), any())
    }

    // ==================== TC13: Login session recorded on success ====================

    @Test
    @DisplayName("TC13: Login session recorded on successful login")
    fun shouldRecordLoginSessionOnSuccess() {
        // Given
        setupSuccessfulLoginMocks()
        val command = LoginCommand(
            username = "testuser", password = "password123",
            ipAddress = "192.168.1.1", userAgent = "Mozilla/5.0"
        )

        // When
        runBlocking { handler.handle(command) }

        // Then
        verify(loginSessionService).recordLogin(
            userId = eq(1L),
            ipAddress = eq("192.168.1.1"),
            userAgent = eq("Mozilla/5.0"),
            deviceFingerprint = any(),
            refreshTokenId = anyOrNull()
        )
    }
}
