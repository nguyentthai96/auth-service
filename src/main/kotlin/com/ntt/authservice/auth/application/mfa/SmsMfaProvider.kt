package com.ntt.authservice.auth.application.mfa

import com.ntt.authservice.auth.application.OtpService
import com.ntt.authservice.auth.application.port.out.NotificationGateway
import org.springframework.stereotype.Component

/**
 * SMS-based MFA provider — generates OTP and sends via SMS.
 * Extracted from MfaService.initiateMfa() when(method) "SMS" branch.
 */
@Component
class SmsMfaProvider(
    private val otpService: OtpService,
    private val notificationGateway: NotificationGateway
) : MfaProvider {

    override val supportedMethod: String = "SMS"

    override fun initiate(userId: Long) {
        val code = otpService.generateOtp(userId, "sms")
        notificationGateway.sendOtp(userId, "sms", code)
    }

    override fun verify(userId: Long, code: String) {
        otpService.verifyOtp(userId, "sms", code)
    }
}
