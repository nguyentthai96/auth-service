package com.ntt.authservice.auth.application

import org.springframework.stereotype.Component

/**
 * Adapter: wraps existing AltchaCaptchaVerifier as a CaptchaStrategy (FR-014).
 * Allows AltchaCaptchaVerifier to participate in CaptchaStrategyRegistry
 * alongside ImageCaptchaStrategy.
 */
@Component
class AltchaCaptchaStrategy(
    private val altchaCaptchaVerifier: AltchaCaptchaVerifier
) : CaptchaStrategy {

    override val type: String = "altcha"

    override fun verify(token: String): Boolean {
        return altchaCaptchaVerifier.verify(token)
    }
}
