package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.adapter.`in`.web.dto.ChangePasswordRequestDto
import com.ntt.authservice.auth.adapter.`in`.web.dto.DataTransferredInfo
import com.ntt.authservice.auth.adapter.`in`.web.dto.ForgotPasswordRequestDto
import com.ntt.authservice.auth.application.DomainLookupService
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.auth.application.PasswordPolicyService
import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.RegisterResult
import com.ntt.authservice.auth.application.command.*
import com.ntt.authservice.auth.application.port.out.NotificationGateway
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import com.ntt.authservice.shared.web.BaseController
import com.ntt.basecore.domain.web.payload.ApiResponse
import com.ntt.authservice.auth.adapter.`in`.web.dto.MfaRequiredResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
@RequestMapping("/auth")
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
    private val domainLookupService: DomainLookupService,
    private val securityProperties: SecurityProperties,
    private val jwtService: JwtService,
    private val notificationGateway: NotificationGateway,
    private val userRepository: UserRepository
) : BaseController() {

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
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val anonymousTokenJti = extractAnonymousTokenJti(request.anonymousToken)

        val command = RegisterCommand(
            username = request.username,
            email = request.email,
            password = request.password,
            fullName = request.fullName,
            phone = request.phone,
            domainCode = request.domainCode,
            anonymousSessionId = request.anonymousSessionId,
            anonymousTokenJti = anonymousTokenJti,
            ipAddress = requestContext.clientIp,
            userAgent = requestContext.userAgent,
            correlationId = requestContext.correlationId
        )
        val result = registerHandler.handle(command)

        // Build response with promotion metadata from RegisterResult (DD-013)
        val successMessage = message("auth.register_success")
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

        return createdResponse(response.copy(message = successMessage), "auth.register_success")
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: com.ntt.authservice.auth.adapter.`in`.web.dto.LoginRequestDto,
        httpResponse: HttpServletResponse
    ): ResponseEntity<ApiResponse<Any>> {
        val anonymousTokenJti = extractAnonymousTokenJti(request.anonymousToken)

        val command = LoginCommand(
            username = request.username,
            password = request.password,
            domainCode = request.domainCode,
            captchaToken = request.captchaToken,
            trustedDeviceHash = request.trustedDeviceHash,
            ipAddress = requestContext.clientIp,
            userAgent = requestContext.userAgent,
            deviceFingerprint = requestContext.deviceFingerprint,
            anonymousSessionId = request.anonymousSessionId,
            anonymousTokenJti = anonymousTokenJti,
            correlationId = requestContext.correlationId
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

                okResponse(responseWithPromotion)
            }
            is LoginResult.MfaRequired -> okResponse(
                MfaRequiredResponse(
                    mfaToken = result.mfaToken,
                    method = result.method,
                    expiresIn = result.expiresIn
                )
            )
        }
    }

    @PostMapping("/logout")
    fun logout(
        httpResponse: HttpServletResponse
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = requestContext.requireUserId()

        // Revoke all active sessions for this user
        loginSessionService.revokeAllSessions(userId, "LOGOUT")

        // Clear refresh token cookie
        clearRefreshTokenCookie(httpResponse)

        return okMessageResponse("auth.logout_success")
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = requestContext.requireUserId()
        val domainId = domainLookupService.getPrimaryDomainId(userId)
        passwordPolicyService.changePassword(userId, request.oldPassword, request.newPassword, domainId)
        return okMessageResponse("auth.password_changed")
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequestDto): ResponseEntity<ApiResponse<Unit>> {
        // Always return 200 to prevent email enumeration
        val user = userRepository.findByEmailAndActiveTrue(request.email)
        if (user != null) {
            val resetToken = java.util.UUID.randomUUID().toString()
            notificationGateway.sendPasswordResetLink(user.id!!, user.email, resetToken)
        }
        return okMessageResponse("auth.password_reset_sent")
    }

    @PostMapping("/refresh")
    fun refresh(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        // Extract refresh token from cookie (preferred) or body
        val refreshToken = extractRefreshTokenFromCookie(httpRequest)
            ?: httpRequest.getParameter("refreshToken")
            ?: throw InvalidCredentialsException()

        val command = RefreshTokenCommand(refreshToken = refreshToken)
        val authToken = refreshTokenHandler.handle(command)

        // Set new refresh token cookie
        setRefreshTokenCookie(httpResponse, authToken.refreshToken)

        val response = AuthResponse.from(authToken).copy(refreshToken = null)
        return okResponse(response)
    }

    @PostMapping("/switch-domain")
    fun switchDomain(
        @Valid @RequestBody request: com.ntt.authservice.auth.adapter.`in`.web.dto.SwitchDomainRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val userId = requestContext.requireUserId()
        val command = SwitchDomainCommand(userId = userId, newDomainCode = request.domainCode)
        val authToken = switchDomainHandler.handle(command)
        return okResponse(AuthResponse.from(authToken).copy(message = message("auth.switch_domain_success")))
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
