package com.ntt.authservice.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request DTOs for auth endpoints.
 */
data class RegisterRequestDto(
    @field:NotBlank val username: String,
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank val fullName: String,
    val phone: String? = null,
    val domainCode: String = "default",
    val anonymousSessionId: String? = null,
    val anonymousToken: String? = null
)

data class LoginRequestDto(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    val domainCode: String? = null,
    val captchaToken: String? = null,
    val trustedDeviceHash: String? = null,
    val deviceFingerprint: String? = null,
    val captchaPayload: String? = null,
    val anonymousSessionId: String? = null,
    val anonymousToken: String? = null
)

data class RefreshTokenRequestDto(
    @field:NotBlank val refreshToken: String
)

data class SwitchDomainRequestDto(
    @field:NotBlank val domainCode: String
)
