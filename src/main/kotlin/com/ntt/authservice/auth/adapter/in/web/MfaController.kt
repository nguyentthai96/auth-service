package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.AuthService
import com.ntt.authservice.auth.application.MfaService
import com.ntt.authservice.auth.application.JwtService
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * MFA endpoints — verify, TOTP setup/confirm, resend OTP, settings.
 */
@RestController
@RequestMapping("/api/auth/mfa")
class MfaController(
    private val mfaService: MfaService,
    private val authService: AuthService,
    private val jwtService: JwtService,
    private val messageSource: MessageSource
) {

    @PostMapping("/verify")
    fun verifyMfa(@Valid @RequestBody request: MfaVerifyRequest): ResponseEntity<Any> {
        val response = mfaService.verifyMfa(
            mfaToken = request.mfaToken,
            code = request.code,
            trustDevice = request.trustDevice,
            deviceHash = request.deviceHash,
            authResponseBuilder = { userId -> authService.buildAuthResponseForUser(userId) }
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/totp/setup")
    fun setupTotp(): ResponseEntity<TotpSetupResponse> {
        val userId = getCurrentUserId()
        val result = mfaService.setupTotp(userId)
        return ResponseEntity.ok(
            TotpSetupResponse(
                secret = result.secret,
                qrCodeUri = result.qrCodeUri,
                issuer = result.issuer
            )
        )
    }

    @PostMapping("/totp/confirm")
    fun confirmTotp(@Valid @RequestBody request: TotpConfirmRequest): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        mfaService.confirmTotp(userId, request.code)
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.totp_confirmed", null, "TOTP setup confirmed successfully", locale)
        return ResponseEntity.ok(mapOf("success" to true, "message" to message))
    }

    @PostMapping("/resend")
    fun resendOtp(@Valid @RequestBody request: MfaResendRequest): ResponseEntity<MfaRequiredResponse> {
        val result = mfaService.resendOtp(request.mfaToken)
        return ResponseEntity.ok(
            MfaRequiredResponse(
                mfaToken = result.mfaToken,
                method = result.method,
                expiresIn = result.expiresIn
            )
        )
    }

    @PutMapping("/settings")
    fun updateSettings(@Valid @RequestBody request: MfaSettingsRequest): ResponseEntity<MfaSettingsResponse> {
        val userId = getCurrentUserId()
        val result = mfaService.updateSettings(userId, request.enabled, request.method)
        return ResponseEntity.ok(
            MfaSettingsResponse(
                mfaEnabled = result.mfaEnabled,
                mfaMethod = result.mfaMethod
            )
        )
    }

    /**
     * Get recovery codes for the authenticated user (FR-001).
     * Returns remaining count only (codes are shown once on generation).
     */
    @GetMapping("/recovery-codes")
    fun getRecoveryCodeCount(): ResponseEntity<Map<String, Long>> {
        val userId = getCurrentUserId()
        val remaining = mfaService.getRemainingRecoveryCodeCount(userId)
        return ResponseEntity.ok(mapOf("remainingCodes" to remaining))
    }

    /**
     * Verify a recovery code as MFA fallback (FR-001).
     * On success, promotes the MFA session to full auth (same as TOTP/OTP verify).
     */
    @PostMapping("/recovery-codes/verify")
    fun verifyRecoveryCode(@Valid @RequestBody request: RecoveryCodeVerifyRequest): ResponseEntity<Any> {
        val response = mfaService.verifyRecoveryCodeMfa(
            mfaToken = request.mfaToken,
            code = request.code,
            authResponseBuilder = { userId -> authService.buildAuthResponseForUser(userId) }
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Regenerate recovery codes — generates 10 new single-use codes (FR-001).
     * Previous codes are invalidated.
     */
    @PostMapping("/recovery-codes/regenerate")
    fun regenerateRecoveryCodes(): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        val codes = mfaService.generateRecoveryCodes(userId)
        val locale = LocaleContextHolder.getLocale()
        val warning = messageSource.getMessage("auth.recovery_codes_warning", null, "Save these codes — they will not be shown again.", locale)
        return ResponseEntity.ok(mapOf(
            "codes" to codes,
            "count" to codes.size,
            "warning" to warning
        ))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw com.ntt.authservice.shared.exception.InvalidCredentialsException()
    }
}
