package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * Pluggable CAPTCHA verification interface.
 * Implementation selected by `app.security.captcha.provider`.
 */
interface CaptchaVerifier {
    fun verify(token: String): Boolean
}

/**
 * Cloudflare Turnstile CAPTCHA adapter.
 */
class TurnstileCaptchaVerifier(
    private val props: SecurityProperties.CaptchaProperties,
    private val restTemplate: RestTemplate
) : CaptchaVerifier {

    private val log = LoggerFactory.getLogger(TurnstileCaptchaVerifier::class.java)

    override fun verify(token: String): Boolean {
        return try {
            val response = restTemplate.postForObject(
                props.verifyUrl.ifBlank { "https://challenges.cloudflare.com/turnstile/v0/siteverify" },
                mapOf("secret" to props.secretKey, "response" to token),
                Map::class.java
            )
            response?.get("success") == true
        } catch (e: Exception) {
            log.error("CAPTCHA verification failed: {}", e.message)
            false
        }
    }
}

/**
 * No-op CAPTCHA verifier — always returns true (for dev/test environments).
 */
class NoopCaptchaVerifier : CaptchaVerifier {
    override fun verify(token: String): Boolean = true
}

/**
 * Configuration to create the correct CaptchaVerifier bean based on properties.
 */
@Configuration
class CaptchaConfig {

    @Bean
    fun captchaVerifier(
        props: SecurityProperties,
        restTemplate: RestTemplate
    ): CaptchaVerifier {
        return when (props.captcha.provider) {
            "turnstile" -> TurnstileCaptchaVerifier(props.captcha, restTemplate)
            else -> NoopCaptchaVerifier()
        }
    }

    @Bean
    fun captchaRestTemplate(): RestTemplate = RestTemplate()
}
