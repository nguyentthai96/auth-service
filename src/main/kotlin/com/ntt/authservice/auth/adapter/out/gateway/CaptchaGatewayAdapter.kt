package com.ntt.authservice.auth.adapter.out.gateway

import com.ntt.authservice.auth.application.port.out.CaptchaGateway
import com.ntt.authservice.auth.application.CaptchaVerifier
import org.springframework.stereotype.Component

/**
 * Adapter that delegates to existing CaptchaVerifier — bridge during migration.
 */
@Component
class CaptchaGatewayAdapter(
    private val captchaVerifier: CaptchaVerifier
) : CaptchaGateway {

    override fun verify(token: String): Boolean {
        return captchaVerifier.verify(token)
    }
}
