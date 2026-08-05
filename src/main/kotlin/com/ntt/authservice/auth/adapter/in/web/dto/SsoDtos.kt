package com.ntt.authservice.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * SSO DTOs for callback, provider info, and identity linking.
 */
data class SsoCallbackRequest(
    @field:NotBlank(message = "Authorization code is required")
    val code: String,

    @field:NotBlank(message = "Provider is required")
    val provider: String,

    @field:NotBlank(message = "Redirect URI is required")
    val redirectUri: String
)

data class SsoProviderInfoDto(
    val id: String,
    val name: String,
    val enabled: Boolean
)

data class SsoLinkRequest(
    @field:NotBlank(message = "Provider is required")
    val provider: String,

    @field:NotBlank(message = "Authorization code is required")
    val code: String,

    @field:NotBlank(message = "Redirect URI is required")
    val redirectUri: String
)
