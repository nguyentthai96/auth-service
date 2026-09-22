package com.ntt.authservice.auth.application.mfa

import com.ntt.authservice.auth.application.OtpService
import com.ntt.authservice.auth.application.port.out.NotificationGateway
import org.springframework.stereotype.Component

/**
 * Email-based MFA provider — generates OTP and sends via email.
 * Extracted from MfaService.initiateMfa() when(method) "EMAIL" branch.
 */
@Component
class EmailMfaProvider(
    private val otpService: OtpService,
    private val notificationGateway: NotificationGateway
) : MfaProvider {

    override val supportedMethod: String = "EMAIL"

    override fun initiate(userId: Long) {
        val code = otpService.generateOtp(userId, "email")
        notificationGateway.sendOtp(userId, "email", code)
    }

    override fun verify(userId: Long, code: String) {
        otpService.verifyOtp(userId, "email", code)
    }
}
