package com.ntt.authservice.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * MFA DTOs for verify, setup, confirm, resend, and settings endpoints.
 */
data class MfaVerifyRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String,

    @field:NotBlank(message = "Verification code is required")
    val code: String
)

data class MfaRequiredResponse(
    val mfaRequired: Boolean = true,
    val mfaToken: String,
    val method: String,
    val expiresIn: Long
)

data class TotpSetupResponse(
    val secret: String,
    val qrCodeUri: String,
    val issuer: String
)

data class TotpConfirmRequest(
    @field:NotBlank(message = "TOTP code is required")
    val code: String
)

data class MfaResendRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String
)

data class MfaSettingsRequest(
    val enabled: Boolean,
    val method: String? = null
)

data class MfaSettingsResponse(
    val mfaEnabled: Boolean,
    val mfaMethod: String
)
