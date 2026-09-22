package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.event.LoginEventRecorder
import com.ntt.authservice.auth.application.pipeline.AuthenticationPipeline
import com.ntt.authservice.shared.exception.*
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

/**
 * Unit tests for LoginHandler — after pipeline refactoring.
 *
 * LoginHandler now delegates to AuthenticationPipeline,
 * so tests mock the pipeline output instead of 17 individual services.
 * Individual step logic is tested separately in each step's test class.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoginHandler Tests (Pipeline-delegated)")
class LoginHandlerTest {

    @Mock private lateinit var authenticationPipeline: AuthenticationPipeline
    @Mock private lateinit var loginEventRecorder: LoginEventRecorder

    private lateinit var handler: LoginHandler

    private val testAuthResponse = AuthResponse(
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
            authenticationPipeline = authenticationPipeline,
            loginEventRecorder = loginEventRecorder
        )
    }

    // ==================== TC1: Successful Login ====================

    @Nested
    @DisplayName("TC1: Successful login (pipeline returns Success)")
    inner class SuccessfulLogin {

        @Test
        @DisplayName("Should return Success when pipeline completes successfully")
        fun shouldLoginSuccessfully() {
            // Given
            val command = LoginCommand(username = "testuser", password = "password123")
            val expectedResult = LoginResult.Success(testAuthResponse)
            whenever(authenticationPipeline.execute(command)).thenReturn(expectedResult)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is LoginResult.Success)
            assertEquals("test-access-token", (result as LoginResult.Success).response.accessToken)
        }

        @Test
        @DisplayName("Should record login success event")
        fun shouldRecordLoginSuccessEvent() {
            // Given
            val command = LoginCommand(username = "testuser", password = "password123")
            val expectedResult = LoginResult.Success(testAuthResponse)
            whenever(authenticationPipeline.execute(command)).thenReturn(expectedResult)

            // When
            handler.handle(command)

            // Then
            verify(loginEventRecorder).recordLoginSuccess(any(), any(), anyOrNull())
        }
    }

    // ==================== TC2: MFA Required ====================

    @Nested
    @DisplayName("TC2: MFA Required (pipeline returns MfaRequired)")
    inner class MfaRequired {

        @Test
        @DisplayName("Should return MfaRequired when pipeline short-circuits")
        fun shouldReturnMfaRequired() {
            // Given
            val command = LoginCommand(username = "mfauser", password = "password123")
            val expectedResult = LoginResult.MfaRequired("mfa-token-123", "OTP_EMAIL", 300)
            whenever(authenticationPipeline.execute(command)).thenReturn(expectedResult)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is LoginResult.MfaRequired)
            assertEquals("mfa-token-123", (result as LoginResult.MfaRequired).mfaToken)
            assertEquals("OTP_EMAIL", result.method)
        }
    }

    // ==================== TC3: Account Locked ====================

    @Test
    @DisplayName("TC3: Account locked → pipeline throws AccountLockedException")
    fun shouldThrowWhenAccountLocked() {
        // Given
        val command = LoginCommand(username = "lockeduser", password = "password123")
        whenever(authenticationPipeline.execute(command))
            .thenThrow(AccountLockedException(java.time.Instant.now().plusSeconds(3600), "Account is locked"))

        // When/Then
        assertThrows<AccountLockedException> { handler.handle(command) }
        verify(loginEventRecorder).recordLoginFailure(any(), anyOrNull())
    }

    // ==================== TC4: Invalid Credentials ====================

    @Test
    @DisplayName("TC4: Invalid credentials → pipeline throws InvalidCredentialsException")
    fun shouldThrowWhenInvalidCredentials() {
        // Given
        val command = LoginCommand(username = "testuser", password = "wrongpassword")
        whenever(authenticationPipeline.execute(command))
            .thenThrow(InvalidCredentialsException())

        // When/Then
        assertThrows<InvalidCredentialsException> { handler.handle(command) }
        verify(loginEventRecorder).recordLoginFailure(any(), anyOrNull())
    }

    // ==================== TC5: CAPTCHA Required ====================

    @Test
    @DisplayName("TC5: CAPTCHA required → pipeline throws CaptchaRequiredException")
    fun shouldThrowWhenCaptchaRequired() {
        // Given
        val command = LoginCommand(username = "captchauser", password = "password123")
        whenever(authenticationPipeline.execute(command))
            .thenThrow(CaptchaRequiredException("CAPTCHA required"))

        // When/Then
        assertThrows<CaptchaRequiredException> { handler.handle(command) }
        verify(loginEventRecorder).recordLoginFailure(any(), anyOrNull())
    }

    // ==================== TC6: Password Expired ====================

    @Test
    @DisplayName("TC6: Password expired → pipeline throws PasswordExpiredException")
    fun shouldThrowWhenPasswordExpired() {
        // Given
        val command = LoginCommand(username = "testuser", password = "password123")
        whenever(authenticationPipeline.execute(command))
            .thenThrow(PasswordExpiredException("Password has expired"))

        // When/Then
        assertThrows<PasswordExpiredException> { handler.handle(command) }
        verify(loginEventRecorder).recordLoginFailure(any(), anyOrNull())
    }

    // ==================== TC7: Session Limit Exceeded ====================

    @Test
    @DisplayName("TC7: Session limit exceeded → pipeline throws SessionLimitExceededException")
    fun shouldThrowWhenSessionLimitExceeded() {
        // Given
        val command = LoginCommand(username = "testuser", password = "password123")
        whenever(authenticationPipeline.execute(command))
            .thenThrow(SessionLimitExceededException(3, 3))

        // When/Then
        assertThrows<SessionLimitExceededException> { handler.handle(command) }
        verify(loginEventRecorder).recordLoginFailure(any(), anyOrNull())
    }

    // ==================== TC8: CommandType ====================

    @Test
    @DisplayName("TC8: commandType() returns LoginCommand class")
    fun shouldReturnCorrectCommandType() {
        assertEquals(LoginCommand::class.java, handler.commandType())
    }
}
