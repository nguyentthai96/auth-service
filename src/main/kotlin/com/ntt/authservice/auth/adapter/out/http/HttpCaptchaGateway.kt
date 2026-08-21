package com.ntt.authservice.auth.adapter.out.http

import com.ntt.authservice.auth.application.port.out.CaptchaGateway
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * CaptchaGateway implementation using @HttpExchange CaptchaClient (FR-019).
 * Circuit breaker protects against captcha provider unavailability.
 * When the circuit is open, captcha verification is auto-passed (graceful degradation).
 */
@Component("httpCaptchaGateway")
@Primary
class HttpCaptchaGateway(
    private val captchaClient: CaptchaClient,
    @Value("\${app.security.captcha.secret-key:}") private val secretKey: String,
    @Value("\${app.security.captcha.provider:noop}") private val provider: String
) : CaptchaGateway {

    private val log = LoggerFactory.getLogger(HttpCaptchaGateway::class.java)

    @CircuitBreaker(name = "captchaProvider", fallbackMethod = "verifyFallback")
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

    /**
     * Circuit breaker fallback — graceful degradation: allow request when captcha provider is down.
     * Logs warning for security review.
     */
    @Suppress("unused")
    private fun verifyFallback(token: String, ex: Throwable): Boolean {
        log.warn("CAPTCHA circuit breaker OPEN — auto-passing verification. Error: {}", ex.message)
        return true
    }
}
