package com.ntt.authservice.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * MFA DTOs for verify, setup, confirm, resend, and settings endpoints.
 */
data class MfaVerifyRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String,

    @field:NotBlank(message = "Verification code is required")
    val code: String,

    /** When true, save device fingerprint hash to skip MFA on subsequent logins. */
    val trustDevice: Boolean = false,

    /** SHA-256 hash of device fingerprint (userAgent + screenRes + timezone). */
    val deviceHash: String? = null
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

/**
 * Recovery codes response — returned on MFA setup and regeneration (FR-001).
 */
data class RecoveryCodesResponse(
    val codes: List<String>,
    val generatedAt: String,
    val totalCodes: Int = codes.size,
    val message: String = "Store these codes securely. Each code can only be used once."
)

/**
 * Recovery code verify request (FR-001).
 * Used to verify a single-use recovery code as MFA fallback.
 */
data class RecoveryCodeVerifyRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String,

    @field:NotBlank(message = "Recovery code is required")
    val code: String
)
