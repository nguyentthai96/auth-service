package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.command.*
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.shared.exception.InvalidCredentialsException
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
    private val buildAuthResponseHandler: BuildAuthResponseHandler
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequestDto): ResponseEntity<AuthResponse> {
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
    fun login(@Valid @RequestBody request: LoginRequestDto): ResponseEntity<Any> {
        val command = LoginCommand(
            username = request.username,
            password = request.password,
            domainCode = request.domainCode,
            captchaToken = request.captchaToken,
            trustedDeviceHash = request.trustedDeviceHash
        )
        val result = loginHandler.handle(command)
        return when (result) {
            is LoginResult.Success -> ResponseEntity.ok(AuthResponse.from(result.response))
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

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequestDto): ResponseEntity<AuthResponse> {
        val command = RefreshTokenCommand(refreshToken = request.refreshToken)
        val authToken = refreshTokenHandler.handle(command)
        return ResponseEntity.ok(AuthResponse.from(authToken))
    }

    @PostMapping("/switch-domain")
    fun switchDomain(
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
}

// DTO classes are reused from the existing AuthController file
