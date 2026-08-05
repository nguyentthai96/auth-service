package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for CAPTCHA verification.
 */
interface CaptchaGateway {
    fun verify(token: String): Boolean
}
