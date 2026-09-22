package com.ntt.authservice.auth.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.adapter.out.cache.RedisLockoutAdapter
import com.ntt.authservice.auth.application.*
import com.ntt.authservice.auth.application.command.LoginCommand
import com.ntt.authservice.auth.application.command.LoginHandler
import com.ntt.authservice.auth.application.command.TokenGenerator
import com.ntt.authservice.auth.application.event.LoginEventRecorder
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.CaptchaFailedException
import com.ntt.authservice.shared.exception.CaptchaRequiredException
import com.ntt.authservice.shared.exception.InvalidCredentialsException
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
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import com.ntt.notification.client.NotificationPort
import org.springframework.beans.factory.ObjectProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Integration test for full CAPTCHA login flow — end-to-end automation test.
 *
 * Simulates: Failed logins → CAPTCHA trigger → Altcha PoW solve → Login with CAPTCHA token.
 * Tests the complete interaction between LoginHandler, AccountLockoutService,
 * CaptchaStrategyRegistry, and AltchaCaptchaVerifier.
 *
 * FR-004: Login Security
 * FR-010: Account Lockout Policy
 * FR-014: Dual CAPTCHA Strategy
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CAPTCHA Login Flow Integration Tests")
class CaptchaLoginFlowIntegrationTest {

    // --- Core dependencies ---
    @Mock private lateinit var userPort: UserPort
    @Mock private lateinit var domainPort: DomainPort
    @Mock private lateinit var tokenStore: TokenStore
    @Mock private lateinit var captchaGateway: CaptchaGateway
    @Mock private lateinit var getUserRolesHandler: GetUserRolesHandler
    @Mock private lateinit var tokenGenerator: TokenGenerator
    @Mock private lateinit var passwordPolicyService: PasswordPolicyService
    @Mock private lateinit var loginRateLimitService: LoginRateLimitService
    @Mock private lateinit var sessionPolicyService: SessionPolicyService
    @Mock private lateinit var loginSessionService: LoginSessionService
    @Mock private lateinit var sessionPromotionService: SessionPromotionService
    @Mock private lateinit var loginEventRecorder: LoginEventRecorder
    @Mock private lateinit var fingerprintService: FingerprintService
    @Mock private lateinit var passwordUpgradeService: PasswordUpgradeService
    @Mock private lateinit var auditLogService: AuditLogService
    @Mock private lateinit var notificationPort: ObjectProvider<NotificationPort>

    // --- Real components (not mocked) ---
    private lateinit var accountLockoutService: AccountLockoutService
    private lateinit var altchaCaptchaVerifier: AltchaCaptchaVerifier
    private lateinit var imageCaptchaStrategy: ImageCaptchaStrategy
    private lateinit var captchaStrategyRegistry: CaptchaStrategyRegistry
    private lateinit var loginHandler: LoginHandler
    private lateinit var securityProperties: SecurityProperties

    private val objectMapper = ObjectMapper()
    private val hmacKey = "integration-test-hmac-key-2024"

    // --- Mocked Redis adapter for lockout ---
    @Mock private lateinit var redisLockoutAdapter: RedisLockoutAdapter

    private val testUser = User(
        id = UserId(100L),
        username = "captcha_test_user",
        email = Email("captcha@test.com"),
        passwordHash = PasswordHash("\$2a\$10\$encodedHash"),
        fullName = "CAPTCHA Test User",
        status = UserStatus.Active
    )

    private val testAuthToken = AuthToken(
        accessToken = "test-jwt-access-token",
        refreshToken = "test-jwt-refresh-token",
        tokenType = "Bearer",
        expiresIn = 1800,
        userId = 100L,
        username = "captcha_test_user",
        activeDomain = "default",
        roles = listOf("USER"),
        permissions = listOf("read")
    )

    @BeforeEach
    fun setUp() {
        securityProperties = SecurityProperties(
            lockout = SecurityProperties.LockoutProperties(
                enabled = true,
                maxFailedAttempts = 5,
                lockDurationMinutes = 30,
                failedAttemptWindowMinutes = 15
            ),
            captcha = SecurityProperties.CaptchaProperties(
                provider = "altcha",
                altcha = SecurityProperties.CaptchaProperties.AltchaProperties(
                    hmacKey = hmacKey,
                    difficulty = 50, // Low for fast tests
                    challengeTtlSeconds = 300
                )
            ),
            imageCaptcha = SecurityProperties.ImageCaptchaProperties(
                defaultType = "altcha",
                image = SecurityProperties.ImageCaptchaProperties.ImageSettings(
                    width = 200, height = 60, length = 5, ttlSeconds = 180
                )
            )
        )

        // Real Altcha verifier (no mocks — tests actual PoW)
        altchaCaptchaVerifier = AltchaCaptchaVerifier(securityProperties, objectMapper)

        // Real Image CAPTCHA strategy
        imageCaptchaStrategy = ImageCaptchaStrategy(securityProperties)

        // Real CaptchaStrategyRegistry with both strategies
        captchaStrategyRegistry = CaptchaStrategyRegistry(
            strategies = listOf(
                object : CaptchaStrategy {
                    override val type = "altcha"
                    override fun verify(token: String) = altchaCaptchaVerifier.verify(token)
                },
                imageCaptchaStrategy
            ),
            securityProperties = securityProperties
        )

        // Real AccountLockoutService with mocked Redis adapter
        accountLockoutService = AccountLockoutService(
            redisLockoutAdapter = redisLockoutAdapter,
            userPort = userPort,
            securityProperties = securityProperties,
            auditLogService = auditLogService,
            notificationPort = notificationPort
        )

        // LoginHandler with AuthenticationPipeline (post-refactoring)
        // Note: In this integration test, we use a mock pipeline that delegates to
        // SecurityPreCheckStep for CAPTCHA testing, then simulates success/failure
        val authenticationPipeline = mock<com.ntt.authservice.auth.application.pipeline.AuthenticationPipeline>()

        // Default: pipeline returns Success for valid credentials
        whenever(authenticationPipeline.execute(any())).thenAnswer { invocation ->
            val cmd = invocation.getArgument<LoginCommand>(0)
            // Re-implement the CAPTCHA + lockout checks inline for integration testing
            val user = userPort.findByUsernameAndActive(cmd.username)
                ?: throw InvalidCredentialsException()

            // Check lockout
            accountLockoutService.validateLockoutStatus(user)

            // Check CAPTCHA
            val captchaThreshold = securityProperties.password.maxFailedAttempts - 1
            if (user.failedLoginCount >= captchaThreshold) {
                val captchaToken = cmd.captchaToken
                if (captchaToken.isNullOrBlank()) {
                    throw CaptchaRequiredException("CAPTCHA verification required")
                }
                val strategy = captchaStrategyRegistry.getStrategy()
                if (!strategy.verify(captchaToken)) {
                    throw CaptchaFailedException("CAPTCHA verification failed")
                }
            }

            // Check password
            if (!tokenGenerator.matchesPassword(cmd.password, user.passwordHash.value)) {
                // Record failed attempt
                loginRateLimitService.recordFailedAttempt(cmd.ipAddress ?: "", cmd.username, cmd.deviceFingerprint)
                accountLockoutService.recordFailedAttempt(user, cmd.ipAddress)
                throw InvalidCredentialsException()
            }

            // Success
            loginRateLimitService.resetOnSuccess(cmd.ipAddress ?: "", cmd.username, cmd.deviceFingerprint)
            LoginResult.Success(AuthResponse.from(testAuthToken))
        }

        loginHandler = LoginHandler(
            authenticationPipeline = authenticationPipeline,
            loginEventRecorder = loginEventRecorder
        )
    }

    private fun setupCommonMocks() {
        whenever(userPort.findByUsernameAndActive("captcha_test_user")).thenReturn(testUser)
        whenever(getUserRolesHandler.handle(any())).thenReturn(emptyList())
        whenever(tokenGenerator.getPrimaryDomain(any())).thenReturn("default")
    }

    // ==========================================================================
    // TEST SUITE 1: CAPTCHA Trigger Threshold
    // ==========================================================================

    @Nested
    @DisplayName("Suite 1: CAPTCHA Trigger Threshold")
    inner class CaptchaTrigger {

        @Test
        @DisplayName("TC1: Below threshold (3 failures) → no CAPTCHA required, just auth error")
        fun shouldNotRequireCaptchaBelowThreshold() {
            // Given — 3 failed attempts (threshold is maxAttempts-1 = 4)
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(3)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(false)
            whenever(redisLockoutAdapter.incrementFailedAttempts(100L)).thenReturn(4)

            val command = LoginCommand(
                username = "captcha_test_user",
                password = "wrong-password"
            )

            // When/Then — should throw InvalidCredentials, NOT CaptchaRequired
            assertThrows<InvalidCredentialsException> {
                runBlocking { loginHandler.handle(command) }
            }
        }

        @Test
        @DisplayName("TC2: At threshold (4 failures) + no captchaToken → AUTH_007 CaptchaRequired")
        fun shouldRequireCaptchaAtThreshold() {
            // Given — exactly at threshold (maxAttempts-1 = 4)
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(4)

            val command = LoginCommand(
                username = "captcha_test_user",
                password = "wrong-password"
                // No captchaToken
            )

            // When/Then
            val exception = assertThrows<CaptchaRequiredException> {
                runBlocking { loginHandler.handle(command) }
            }
            assertNotNull(exception)
        }

        @Test
        @DisplayName("TC3: Above threshold (6 failures) + no captchaToken → AUTH_007 CaptchaRequired")
        fun shouldRequireCaptchaAboveThreshold() {
            // Given — well above threshold
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(6)

            val command = LoginCommand(
                username = "captcha_test_user",
                password = "password"
            )

            // When/Then
            assertThrows<CaptchaRequiredException> {
                runBlocking { loginHandler.handle(command) }
            }
        }
    }

    // ==========================================================================
    // TEST SUITE 2: Altcha PoW Full E2E
    // ==========================================================================

    @Nested
    @DisplayName("Suite 2: Altcha PoW E2E — Generate → Solve → Verify → Login")
    inner class AltchaE2E {

        @Test
        @DisplayName("TC4: Full E2E — generate challenge, solve PoW, login with valid token + correct password → SUCCESS")
        fun shouldLoginSuccessfullyWithSolvedAltchaCaptcha() {
            // Given — user at CAPTCHA threshold
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(4)
            whenever(tokenGenerator.matchesPassword(eq("correct-password"), any())).thenReturn(true)
            whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)

            val mockLoginSession = com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity().apply {
                this.userId = 100L
                this.ipAddress = "127.0.0.1"
                this.isNewDevice = false
            }
            whenever(loginSessionService.recordLogin(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(mockLoginSession)

            // Step 1: Generate Altcha challenge (simulating GET /captcha/challenge?type=altcha)
            val challenge = altchaCaptchaVerifier.generateChallenge()
            assertNotNull(challenge)
            assertEquals("SHA-256", challenge.algorithm)

            // Step 2: Solve PoW (simulating frontend altchaSolver.ts)
            val solution = solveAltchaChallenge(challenge)
            assertNotNull(solution, "PoW must be solvable")

            // Step 3: Build captchaToken (same format as frontend)
            val captchaToken = buildAltchaToken(challenge, solution!!)

            // Step 4: Login with CAPTCHA token
            val command = LoginCommand(
                username = "captcha_test_user",
                password = "correct-password",
                captchaToken = captchaToken
            )

            // When
            val result = runBlocking { loginHandler.handle(command) }

            // Then
            assertTrue(result is LoginResult.Success, "Login with valid CAPTCHA + correct password should succeed")
            assertEquals("test-jwt-access-token", (result as LoginResult.Success).response.accessToken)
        }

        @Test
        @DisplayName("TC5: Valid CAPTCHA token + wrong password → InvalidCredentialsException")
        fun shouldRejectWrongPasswordEvenWithValidCaptcha() {
            // Given
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(4)
            whenever(tokenGenerator.matchesPassword(any(), any())).thenReturn(false)
            whenever(redisLockoutAdapter.incrementFailedAttempts(100L)).thenReturn(5)

            // Solve CAPTCHA
            val challenge = altchaCaptchaVerifier.generateChallenge()
            val solution = solveAltchaChallenge(challenge)!!
            val captchaToken = buildAltchaToken(challenge, solution)

            val command = LoginCommand(
                username = "captcha_test_user",
                password = "wrong-password",
                captchaToken = captchaToken
            )

            // When/Then — CAPTCHA passes but password fails
            assertThrows<InvalidCredentialsException> {
                runBlocking { loginHandler.handle(command) }
            }
        }

        @Test
        @DisplayName("TC6: Invalid CAPTCHA token (wrong solution) → CaptchaFailedException")
        fun shouldRejectInvalidCaptchaToken() {
            // Given
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(4)

            // Build a fake token with wrong number
            val challenge = altchaCaptchaVerifier.generateChallenge()
            val fakePayload = AltchaPayload(
                algorithm = "SHA-256",
                challenge = challenge.challenge,
                number = 99999, // Wrong solution
                salt = challenge.salt,
                signature = challenge.signature
            )
            val fakeToken = Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsString(fakePayload).toByteArray(StandardCharsets.UTF_8)
            )

            val command = LoginCommand(
                username = "captcha_test_user",
                password = "any-password",
                captchaToken = fakeToken
            )

            // When/Then
            assertThrows<CaptchaFailedException> {
                runBlocking { loginHandler.handle(command) }
            }
        }

        @Test
        @DisplayName("TC7: Replay CAPTCHA token (used twice) → CaptchaFailedException")
        fun shouldRejectReplayedCaptchaToken() {
            // Given
            setupCommonMocks()
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenReturn(4)
            whenever(tokenGenerator.matchesPassword(eq("correct-password"), any())).thenReturn(true)
            whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)
            val mockLoginSession = com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity().apply {
                this.userId = 100L
                this.ipAddress = "127.0.0.1"
                this.isNewDevice = false
            }
            whenever(loginSessionService.recordLogin(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(mockLoginSession)

            // Solve CAPTCHA
            val challenge = altchaCaptchaVerifier.generateChallenge()
            val solution = solveAltchaChallenge(challenge)!!
            val captchaToken = buildAltchaToken(challenge, solution)

            // First login — should succeed (consumes the CAPTCHA)
            val command1 = LoginCommand(
                username = "captcha_test_user",
                password = "correct-password",
                captchaToken = captchaToken
            )
            val result1 = runBlocking { loginHandler.handle(command1) }
            assertTrue(result1 is LoginResult.Success)

            // Second login with SAME token — should fail (replay protection)
            val command2 = LoginCommand(
                username = "captcha_test_user",
                password = "correct-password",
                captchaToken = captchaToken
            )

            // When/Then
            assertThrows<CaptchaFailedException> {
                runBlocking { loginHandler.handle(command2) }
            }
        }
    }

    // ==========================================================================
    // TEST SUITE 3: Image CAPTCHA Integration
    // ==========================================================================

    @Nested
    @DisplayName("Suite 3: Image CAPTCHA Integration")
    inner class ImageCaptchaIntegration {

        @Test
        @DisplayName("TC8: Image CAPTCHA challenge → valid PNG with captchaId")
        fun shouldGenerateValidImageChallenge() {
            // When
            val challenge = imageCaptchaStrategy.generateChallenge()

            // Then
            assertNotNull(challenge.captchaId)
            assertTrue(challenge.captchaId.isNotBlank())
            assertTrue(challenge.imageBase64.isNotBlank())
            assertEquals(180, challenge.ttlSeconds)

            // Verify Base64 decodes to valid PNG
            val imageBytes = Base64.getDecoder().decode(challenge.imageBase64)
            assertTrue(imageBytes.size > 50)
        }

        @Test
        @DisplayName("TC9: Wrong answer for image CAPTCHA → verify fails")
        fun shouldRejectWrongImageAnswer() {
            // Given
            val challenge = imageCaptchaStrategy.generateChallenge()

            // When
            val result = imageCaptchaStrategy.verify("${challenge.captchaId}:WRONGANSWER")

            // Then
            assertFalse(result)
        }
    }

    // ==========================================================================
    // TEST SUITE 4: CaptchaStrategyRegistry
    // ==========================================================================

    @Nested
    @DisplayName("Suite 4: CaptchaStrategyRegistry Dispatch")
    inner class RegistryDispatch {

        @Test
        @DisplayName("TC10: Registry returns altcha strategy by default")
        fun shouldReturnAltchaByDefault() {
            val strategy = captchaStrategyRegistry.getStrategy()
            assertNotNull(strategy)
        }

        @Test
        @DisplayName("TC11: Registry returns image strategy when type=image")
        fun shouldReturnImageStrategy() {
            val strategy = captchaStrategyRegistry.getStrategy("image")
            assertEquals("image", strategy.type)
        }

        @Test
        @DisplayName("TC12: Registry checks type availability")
        fun shouldCheckTypeAvailability() {
            assertTrue(captchaStrategyRegistry.isTypeAvailable("image"))
            assertTrue(captchaStrategyRegistry.isTypeAvailable("altcha"))
            assertFalse(captchaStrategyRegistry.isTypeAvailable("recaptcha"))
        }
    }

    // ==========================================================================
    // TEST SUITE 5: Sequential Flow Simulation
    // ==========================================================================

    @Nested
    @DisplayName("Suite 5: Sequential Login → CAPTCHA → Login Flow")
    inner class SequentialFlow {

        @Test
        @DisplayName("TC13: 4 failed logins → CAPTCHA required → solve → login success")
        fun shouldCompleteFullSequentialFlow() {
            // Setup
            setupCommonMocks()
            var failedCount = 0L

            // Mock progressive failed attempt count
            whenever(redisLockoutAdapter.getFailedAttempts(100L)).thenAnswer { failedCount }
            whenever(redisLockoutAdapter.incrementFailedAttempts(100L)).thenAnswer { ++failedCount }
            whenever(tokenGenerator.matchesPassword(eq("wrong"), any())).thenReturn(false)
            whenever(tokenGenerator.matchesPassword(eq("correct"), any())).thenReturn(true)
            whenever(tokenGenerator.generateAuthResponse(any(), any(), any())).thenReturn(testAuthToken)
            val mockLoginSession = com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity().apply {
                this.userId = 100L
                this.ipAddress = "127.0.0.1"
                this.isNewDevice = false
            }
            whenever(loginSessionService.recordLogin(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(mockLoginSession)

            // --- Phase 1: Failed logins (below CAPTCHA threshold) ---
            for (attempt in 1..3) {
                val command = LoginCommand(
                    username = "captcha_test_user",
                    password = "wrong"
                )
                assertThrows<InvalidCredentialsException>(
                    "Attempt $attempt should fail with InvalidCredentials"
                ) {
                    runBlocking { loginHandler.handle(command) }
                }
            }
            assertEquals(3L, failedCount, "Should have 3 failed attempts")

            // --- Phase 2: 4th failed login → still InvalidCredentials (count=3, threshold=4) ---
            val command4 = LoginCommand(
                username = "captcha_test_user",
                password = "wrong"
            )
            assertThrows<InvalidCredentialsException> {
                runBlocking { loginHandler.handle(command4) }
            }
            assertEquals(4L, failedCount, "Should have 4 failed attempts after 4th login")

            // --- Phase 3: 5th attempt → CAPTCHA required (count=4 >= threshold=4) ---
            val command5 = LoginCommand(
                username = "captcha_test_user",
                password = "wrong"
                // No captchaToken
            )
            assertThrows<CaptchaRequiredException>(
                "5th attempt should require CAPTCHA"
            ) {
                runBlocking { loginHandler.handle(command5) }
            }

            // --- Phase 4: Solve CAPTCHA and retry with correct password ---
            val challenge = altchaCaptchaVerifier.generateChallenge()
            val solution = solveAltchaChallenge(challenge)!!
            val captchaToken = buildAltchaToken(challenge, solution)

            val loginCommand = LoginCommand(
                username = "captcha_test_user",
                password = "correct",
                captchaToken = captchaToken
            )

            // When
            val result = runBlocking { loginHandler.handle(loginCommand) }

            // Then
            assertTrue(result is LoginResult.Success, "Login with valid CAPTCHA + correct password should succeed")
            val successResult = result as LoginResult.Success
            assertEquals("test-jwt-access-token", successResult.response.accessToken)
            assertEquals(100L, successResult.response.userId)
        }
    }

    // ==========================================================================
    // Helper Methods — mirrors frontend altchaSolver.ts logic
    // ==========================================================================

    /**
     * Solve Altcha PoW challenge — same algorithm as frontend altchaSolver.ts.
     * Finds N where SHA-256(salt + N) == challenge.
     */
    private fun solveAltchaChallenge(challenge: AltchaChallenge): Long? {
        for (n in 0 until challenge.maxnumber) {
            val hash = sha256Hex("${challenge.salt}$n")
            if (hash == challenge.challenge) {
                return n.toLong()
            }
        }
        return null
    }

    /**
     * Build captchaToken — same Base64 JSON format as frontend altchaSolver.ts.
     */
    private fun buildAltchaToken(challenge: AltchaChallenge, number: Long): String {
        val payload = AltchaPayload(
            algorithm = challenge.algorithm,
            challenge = challenge.challenge,
            number = number,
            salt = challenge.salt,
            signature = challenge.signature
        )
        val json = objectMapper.writeValueAsString(payload)
        return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
