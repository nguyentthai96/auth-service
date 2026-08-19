package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.adapter.`in`.web.dto.DataTransferredInfo
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.auth.application.PasswordPolicyService
import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.RegisterResult
import com.ntt.authservice.auth.application.command.*
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * CQRS-based AuthController — dispatches to command/query handlers instead of AuthService.
 * Active when app.security.cqrs.enabled = true.
 *
 * FR-009: Controller rewire to CQRS pattern.
 * FR-003, FR-013: Anonymous session promotion support in login/register.
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
    private val securityProperties: SecurityProperties,
    private val messageSource: MessageSource,
    private val jwtService: JwtService
) {

    private val log = org.slf4j.LoggerFactory.getLogger(CqrsAuthController::class.java)

    /**
     * Extract JTI from anonymous token string. Returns null if token is invalid or expired.
     * Graceful: promotion is best-effort — invalid token means JTI won't be blacklisted.
     */
    private fun extractAnonymousTokenJti(anonymousToken: String?): String? {
        if (anonymousToken.isNullOrBlank()) return null
        return try {
            jwtService.parseAnonymousToken(anonymousToken).id
        } catch (e: Exception) {
            log.warn("Failed to parse anonymous token for JTI extraction: {}", e.message)
            null
        }
    }

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: com.ntt.authservice.auth.adapter.`in`.web.dto.RegisterRequestDto
    ): ResponseEntity<AuthResponse> {
        val anonymousTokenJti = extractAnonymousTokenJti(request.anonymousToken)

        val command = RegisterCommand(
            username = request.username,
            email = request.email,
            password = request.password,
            fullName = request.fullName,
            phone = request.phone,
            domainCode = request.domainCode,
            anonymousSessionId = request.anonymousSessionId,
            anonymousTokenJti = anonymousTokenJti
        )
        val result = registerHandler.handle(command)

        // Build response with promotion metadata from RegisterResult (DD-013)
        val locale = LocaleContextHolder.getLocale()
        val successMessage = messageSource.getMessage("auth.register_success", null, "Registration successful", locale)
        val response = when (result) {
            is RegisterResult.Success -> {
                val promotionResult = result.promotionResult
                val baseResponse = AuthResponse.from(result.authToken)
                if (promotionResult != null && promotionResult.status == PromotionResult.Status.SUCCESS) {
                    baseResponse.copy(
                        promotedFromAnonymous = true,
                        dataTransferred = DataTransferredInfo(
                            itemCount = promotionResult.itemCount,
                            namespaces = promotionResult.namespaces,
                            status = promotionResult.status.name
                        )
                    )
                } else if (promotionResult != null) {
                    baseResponse.copy(
                        promotedFromAnonymous = false,
                        dataTransferred = DataTransferredInfo(
                            itemCount = promotionResult.itemCount,
                            namespaces = promotionResult.namespaces,
                            status = promotionResult.status.name
                        )
                    )
                } else {
                    baseResponse
                }
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response.copy(message = successMessage))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: com.ntt.authservice.auth.adapter.`in`.web.dto.LoginRequestDto,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<Any> {
        val anonymousTokenJti = extractAnonymousTokenJti(request.anonymousToken)

        val command = LoginCommand(
            username = request.username,
            password = request.password,
            domainCode = request.domainCode,
            captchaToken = request.captchaToken,
            trustedDeviceHash = request.trustedDeviceHash,
            ipAddress = extractClientIp(httpRequest),
            userAgent = httpRequest.getHeader("User-Agent"),
            deviceFingerprint = httpRequest.getHeader("X-Device-Fingerprint"),
            anonymousSessionId = request.anonymousSessionId,
            anonymousTokenJti = anonymousTokenJti
        )
        val result = loginHandler.handle(command)
        return when (result) {
            is LoginResult.Success -> {
                // Set refresh token as HttpOnly cookie
                setRefreshTokenCookie(httpResponse, result.response.refreshToken)

                // Build response with promotion metadata
                val promotionResult = result.promotionResult
                val responseWithPromotion = if (promotionResult != null) {
                    result.response.copy(
                        refreshToken = null,
                        promotedFromAnonymous = promotionResult.status == PromotionResult.Status.SUCCESS,
                        dataTransferred = DataTransferredInfo(
                            itemCount = promotionResult.itemCount,
                            namespaces = promotionResult.namespaces,
                            status = promotionResult.status.name
                        )
                    )
                } else {
                    result.response.copy(refreshToken = null)
                }

                ResponseEntity.ok(responseWithPromotion)
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

        val message = messageSource.getMessage("auth.logout_success", null, LocaleContextHolder.getLocale())
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()
        // TODO: resolve domainId from user's active domain
        passwordPolicyService.changePassword(userId, request.oldPassword, request.newPassword, 0L)
        val message = messageSource.getMessage("auth.password_changed", null, LocaleContextHolder.getLocale())
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequestDto): ResponseEntity<Map<String, String>> {
        // Always return 200 to prevent email enumeration
        // TODO: trigger password reset email
        val message = messageSource.getMessage("auth.password_reset_sent", null, "If the email exists, a reset link has been sent", LocaleContextHolder.getLocale())
            ?: "If the email exists, a reset link has been sent"
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PostMapping("/refresh")
    fun refresh(
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
    fun switchDomain(
        @RequestBody request: com.ntt.authservice.auth.adapter.`in`.web.dto.SwitchDomainRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<AuthResponse> {
        val userId = getCurrentUserId()
        val command = SwitchDomainCommand(userId = userId, newDomainCode = request.domainCode)
        val authToken = switchDomainHandler.handle(command)
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.switch_domain_success", null, "Domain switched successfully", locale)
        return ResponseEntity.ok(AuthResponse.from(authToken).copy(message = message))
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
