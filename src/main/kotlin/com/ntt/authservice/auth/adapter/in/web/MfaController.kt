package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.AuthService
import com.ntt.authservice.auth.application.MfaService
import jakarta.validation.Valid
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
    private val authService: AuthService
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
    fun confirmTotp(@Valid @RequestBody request: TotpConfirmRequest): ResponseEntity<Map<String, Boolean>> {
        val userId = getCurrentUserId()
        mfaService.confirmTotp(userId, request.code)
        return ResponseEntity.ok(mapOf("success" to true))
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

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw com.ntt.authservice.shared.exception.InvalidCredentialsException()
    }
}
