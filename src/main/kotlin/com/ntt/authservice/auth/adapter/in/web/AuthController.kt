package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequestDto): ResponseEntity<AuthResponse> {
        val result = authService.register(
            RegisterRequest(
                username = request.username,
                email = request.email,
                password = request.password,
                fullName = request.fullName,
                phone = request.phone,
                domainCode = request.domainCode
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequestDto): ResponseEntity<AuthResponse> {
        val result = authService.login(
            LoginRequest(
                username = request.username,
                password = request.password,
                domainCode = request.domainCode
            )
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequestDto): ResponseEntity<AuthResponse> {
        val result = authService.refreshToken(request.refreshToken)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/switch-domain")
    fun switchDomain(
        @RequestBody request: SwitchDomainRequestDto,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<AuthResponse> {
        val userId = (org.springframework.security.core.context.SecurityContextHolder
            .getContext().authentication?.principal as? String)?.toLong()
            ?: throw com.ntt.authservice.shared.exception.InvalidCredentialsException()
        val result = authService.switchDomain(userId, request.domainCode)
        return ResponseEntity.ok(result)
    }
}

// Request DTOs with validation
data class RegisterRequestDto(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 100, message = "Username must be 3-100 characters")
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank(message = "Full name is required")
    val fullName: String,

    val phone: String? = null,

    @field:NotBlank(message = "Domain code is required")
    val domainCode: String
)

data class LoginRequestDto(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    val password: String,

    val domainCode: String? = null
)

data class RefreshTokenRequestDto(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

data class SwitchDomainRequestDto(
    @field:NotBlank(message = "Domain code is required")
    val domainCode: String
)
