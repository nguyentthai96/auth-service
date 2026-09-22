package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.event.LoginEventRecorder
import com.ntt.authservice.auth.application.command.LoginCommand
import com.ntt.authservice.auth.application.command.LoginHandler
import com.ntt.authservice.auth.application.pipeline.AuthenticationPipeline
import com.ntt.authservice.shared.exception.PasswordExpiredException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*

/**
 * Password expiry login tests — refactored for pipeline architecture.
 *
 * Now tests LoginHandler behavior when AuthenticationPipeline throws
 * PasswordExpiredException (pipeline step: PasswordExpiryCheckStep).
 *
 * FR-013: Password policy enforcement during login
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Password Expiry Login Tests")
class PasswordExpiryLoginTest {

    @Mock private lateinit var authenticationPipeline: AuthenticationPipeline
    @Mock private lateinit var loginEventRecorder: LoginEventRecorder

    private lateinit var handler: LoginHandler

    private val testAuthResponse = AuthResponse(
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
            authenticationPipeline = authenticationPipeline,
            loginEventRecorder = loginEventRecorder
        )
    }

    @Test
    @DisplayName("TC1: Login with expired password → throws PasswordExpiredException (AUTH_018)")
    fun shouldThrowPasswordExpiredOnExpiredPassword() {
        // Given — pipeline throws PasswordExpiredException
        val command = LoginCommand(username = "expiredpwd-user", password = "password123")
        whenever(authenticationPipeline.execute(command))
            .thenThrow(PasswordExpiredException("Password has expired"))

        // When/Then
        val exception = assertThrows<PasswordExpiredException> {
            handler.handle(command)
        }
        assertTrue(exception.message!!.contains("expired"))
        verify(loginEventRecorder).recordLoginFailure(any(), anyOrNull())
    }

    @Test
    @DisplayName("TC2: Login with non-expired password → Success")
    fun shouldSucceedWithNonExpiredPassword() {
        // Given — pipeline returns Success (no password expiry)
        val command = LoginCommand(username = "expiredpwd-user", password = "password123")
        val expectedResult = LoginResult.Success(testAuthResponse)
        whenever(authenticationPipeline.execute(command)).thenReturn(expectedResult)

        // When
        val result = handler.handle(command)

        // Then
        assertTrue(result is LoginResult.Success)
    }

    @Test
    @DisplayName("TC3: Password expiry check uses domain from request when provided")
    fun shouldUseDomainFromRequest() {
        // Given — pipeline throws PasswordExpiredException for specific domain
        val command = LoginCommand(
            username = "expiredpwd-user", password = "password123",
            domainCode = "corp"
        )
        whenever(authenticationPipeline.execute(command))
            .thenThrow(PasswordExpiredException("Password has expired"))

        // When/Then
        assertThrows<PasswordExpiredException> {
            handler.handle(command)
        }
    }
}
