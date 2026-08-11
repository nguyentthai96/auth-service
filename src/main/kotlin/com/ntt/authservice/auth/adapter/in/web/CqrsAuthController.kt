package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.auth.application.PasswordPolicyService
import com.ntt.authservice.auth.application.command.*
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * CQRS-based AuthController — dispatches to command/query handlers instead of AuthService.
 * Active when app.security.cqrs.enabled = true.
 *
 * FR-009: Controller rewire to CQRS pattern.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "true", matchIfMissing = true)
class CqrsAuthController(
    private val loginHandler: LoginHandler,
    private val registerHandler: RegisterHandler,
    private val refreshTokenHandler: RefreshTokenHandler,
    private val switchDomainHandler: SwitchDomainHandler,
    private val revokeSessionsHandler: RevokeSessionsHandler,
    private val buildAuthResponseHandler: BuildAuthResponseHandler,
    private val passwordPolicyService: PasswordPolicyService,
    private val loginSessionService: LoginSessionService,
    private val securityProperties: SecurityProperties
) {

    @PostMapping("/register")
    suspend fun register(@Valid @RequestBody request: RegisterRequestDto): ResponseEntity<AuthResponse> {
        val command = RegisterCommand(
            username = request.username,
            email = request.email,
            password = request.password,
            fullName = request.fullName,
            phone = request.phone,
            domainCode = request.domainCode
        )
        val authToken = registerHandler.handle(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(authToken))
    }

    @PostMapping("/login")
    suspend fun login(
        @Valid @RequestBody request: LoginRequestDto,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<Any> {
        val command = LoginCommand(
            username = request.username,
            password = request.password,
            domainCode = request.domainCode,
            captchaToken = request.captchaToken,
            trustedDeviceHash = request.trustedDeviceHash,
            ipAddress = extractClientIp(httpRequest),
            userAgent = httpRequest.getHeader("User-Agent"),
            deviceFingerprint = httpRequest.getHeader("X-Device-Fingerprint")
        )
        val result = loginHandler.handle(command)
        return when (result) {
            is LoginResult.Success -> {
                // Set refresh token as HttpOnly cookie
                setRefreshTokenCookie(httpResponse, result.response.refreshToken)

                // Remove refresh token from response body (cookie-only)
                val sanitizedResponse = result.response.copy(refreshToken = null)
                ResponseEntity.ok(sanitizedResponse)
            }
            is LoginResult.MfaRequired -> ResponseEntity.ok(
                mapOf(
                    "mfaRequired" to true,
                    "mfaToken" to result.mfaToken,
                    "method" to result.method,
                    "expiresIn" to result.expiresIn
                )
            )
        }
    }

    @PostMapping("/logout")
    fun logout(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()

        // Revoke all active sessions for this user
        loginSessionService.revokeAllSessions(userId, "LOGOUT")

        // Clear refresh token cookie
        clearRefreshTokenCookie(httpResponse)

        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()
        // TODO: resolve domainId from user's active domain
        passwordPolicyService.changePassword(userId, request.oldPassword, request.newPassword, 0L)
        return ResponseEntity.ok(mapOf("message" to "Password changed successfully"))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequestDto): ResponseEntity<Map<String, String>> {
        // Always return 200 to prevent email enumeration
        // TODO: trigger password reset email
        return ResponseEntity.ok(mapOf("message" to "If the email exists, a reset link has been sent"))
    }

    @PostMapping("/refresh")
    suspend fun refresh(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        // Extract refresh token from cookie (preferred) or body
        val refreshToken = extractRefreshTokenFromCookie(httpRequest)
            ?: httpRequest.getParameter("refreshToken")
            ?: throw InvalidCredentialsException()

        val command = RefreshTokenCommand(refreshToken = refreshToken)
        val authToken = refreshTokenHandler.handle(command)

        // Set new refresh token cookie
        setRefreshTokenCookie(httpResponse, authToken.refreshToken)

        val response = AuthResponse.from(authToken).copy(refreshToken = null)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/switch-domain")
    suspend fun switchDomain(
        @RequestBody request: SwitchDomainRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<AuthResponse> {
        val userId = getCurrentUserId()
        val command = SwitchDomainCommand(userId = userId, newDomainCode = request.domainCode)
        val authToken = switchDomainHandler.handle(command)
        return ResponseEntity.ok(AuthResponse.from(authToken))
    }

    private fun getCurrentUserId(): Long {
        return (org.springframework.security.core.context.SecurityContextHolder
            .getContext().authentication?.principal as? String)?.toLong()
            ?: throw InvalidCredentialsException()
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }

    private fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String?) {
        if (refreshToken == null) return
        val cookie = Cookie("refresh_token", refreshToken).apply {
            isHttpOnly = true
            secure = true
            path = "/api/auth/refresh"
            maxAge = (securityProperties.jwt.refreshTokenExpirationMs / 1000).toInt()
            setAttribute("SameSite", "Strict")
        }
        response.addCookie(cookie)
    }

    private fun clearRefreshTokenCookie(response: HttpServletResponse) {
        val cookie = Cookie("refresh_token", "").apply {
            isHttpOnly = true
            secure = true
            path = "/api/auth/refresh"
            maxAge = 0
            setAttribute("SameSite", "Strict")
        }
        response.addCookie(cookie)
    }

    private fun extractRefreshTokenFromCookie(request: HttpServletRequest): String? {
        return request.cookies?.find { it.name == "refresh_token" }?.value
    }
}
