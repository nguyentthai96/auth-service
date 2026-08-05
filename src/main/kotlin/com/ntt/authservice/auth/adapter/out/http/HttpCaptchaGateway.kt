package com.ntt.authservice.auth.adapter.out.http

import com.ntt.authservice.auth.application.port.out.CaptchaGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * CaptchaGateway implementation using @HttpExchange CaptchaClient.
 * Replaces CaptchaVerifier's manual RestTemplate calls.
 */
@Component("httpCaptchaGateway")
class HttpCaptchaGateway(
    private val captchaClient: CaptchaClient,
    @Value("\${app.security.captcha.secret-key:}") private val secretKey: String,
    @Value("\${app.security.captcha.provider:noop}") private val provider: String
) : CaptchaGateway {

    private val log = LoggerFactory.getLogger(HttpCaptchaGateway::class.java)

    override fun verify(token: String): Boolean {
        if (provider == "noop") {
            log.debug("CAPTCHA provider is noop — auto-verify")
            return true
        }

        return try {
            val response = captchaClient.verify(secret = secretKey, response = token)
            response.success
        } catch (ex: Exception) {
            log.error("CAPTCHA verification failed: {}", ex.message)
            false
        }
    }
}
