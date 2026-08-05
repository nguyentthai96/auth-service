package com.ntt.authservice.auth.adapter.out.http

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

/**
 * Declarative HTTP client for CAPTCHA verification — replaces manual RestTemplate calls.
 * Spring Boot 4.x @HttpExchange with service group configuration.
 *
 * Config in application.yml:
 * spring.http.client.service.captcha.base-url: ${CAPTCHA_VERIFY_URL}
 */
@HttpExchange
interface CaptchaClient {

    @PostExchange
    fun verify(
        @RequestParam("secret") secret: String,
        @RequestParam("response") response: String
    ): CaptchaVerifyResponse
}

data class CaptchaVerifyResponse(
    val success: Boolean,
    val score: Double? = null,
    val action: String? = null,
    val errorCodes: List<String>? = null
)
