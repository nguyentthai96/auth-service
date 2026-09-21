package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.MfaService
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.shared.web.AuthenticatedController
import com.ntt.basecore.domain.web.payload.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * MFA endpoints — verify, TOTP setup/confirm, resend OTP, settings.
 */
@RestController
@RequestMapping("/auth/mfa")
class MfaController(
    private val mfaService: MfaService,
    private val buildAuthResponseHandler: BuildAuthResponseHandler,
    private val jwtService: JwtService
) : AuthenticatedController() {

    @PostMapping("/verify")
    fun verifyMfa(@Valid @RequestBody request: MfaVerifyRequest): ResponseEntity<ApiResponse<Any>> {
        val response = mfaService.verifyMfa(
            mfaToken = request.mfaToken,
            code = request.code,
            trustDevice = request.trustDevice,
            deviceHash = request.deviceHash,
            authResponseBuilder = { userId ->
                AuthResponse.from(buildAuthResponseHandler.handle(BuildAuthResponseQuery(userId)))
            }
        )
        return okResponse(response)
    }

    @PostMapping("/totp/setup")
    fun setupTotp(): ResponseEntity<ApiResponse<TotpSetupResponse>> {
        val result = mfaService.setupTotp(currentUserId())
        return okResponse(
            TotpSetupResponse(
                secret = result.secret,
                qrCodeUri = result.qrCodeUri,
                issuer = result.issuer
            )
        )
    }

    @PostMapping("/totp/confirm")
    fun confirmTotp(@Valid @RequestBody request: TotpConfirmRequest): ResponseEntity<ApiResponse<Unit>> {
        mfaService.confirmTotp(currentUserId(), request.code)
        return okMessageResponse("auth.totp_confirmed")
    }

    @PostMapping("/resend")
    fun resendOtp(@Valid @RequestBody request: MfaResendRequest): ResponseEntity<ApiResponse<MfaRequiredResponse>> {
        val result = mfaService.resendOtp(request.mfaToken)
        return okResponse(
            MfaRequiredResponse(
                mfaToken = result.mfaToken,
                method = result.method,
                expiresIn = result.expiresIn
            )
        )
    }

    @PutMapping("/settings")
    fun updateSettings(@Valid @RequestBody request: MfaSettingsRequest): ResponseEntity<ApiResponse<MfaSettingsResponse>> {
        val result = mfaService.updateSettings(currentUserId(), request.enabled, request.method)
        return okResponse(
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
    fun getRecoveryCodeCount(): ResponseEntity<ApiResponse<RecoveryCodeCountResult>> {
        val remaining = mfaService.getRemainingRecoveryCodeCount(currentUserId())
        return okResponse(RecoveryCodeCountResult(remainingCodes = remaining))
    }

    /**
     * Verify a recovery code as MFA fallback (FR-001).
     * On success, promotes the MFA session to full auth (same as TOTP/OTP verify).
     */
    @PostMapping("/recovery-codes/verify")
    fun verifyRecoveryCode(@Valid @RequestBody request: RecoveryCodeVerifyRequest): ResponseEntity<ApiResponse<Any>> {
        val response = mfaService.verifyRecoveryCodeMfa(
            mfaToken = request.mfaToken,
            code = request.code,
            authResponseBuilder = { userId ->
                AuthResponse.from(buildAuthResponseHandler.handle(BuildAuthResponseQuery(userId)))
            }
        )
        return okResponse(response)
    }

    /**
     * Regenerate recovery codes — generates 10 new single-use codes (FR-001).
     * Previous codes are invalidated.
     */
    @PostMapping("/recovery-codes/regenerate")
    fun regenerateRecoveryCodes(): ResponseEntity<ApiResponse<RecoveryCodesResult>> {
        val codes = mfaService.generateRecoveryCodes(currentUserId())
        return okResponse(
            RecoveryCodesResult(codes = codes, count = codes.size),
            "auth.recovery_codes_warning"
        )
    }
}

