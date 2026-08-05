package com.ntt.authservice.shared.config

import com.ntt.authservice.auth.adapter.out.http.CaptchaClient
import com.ntt.authservice.auth.adapter.out.http.SsoProviderClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * Configuration for @HttpExchange declarative clients.
 * Spring Boot 4.x HTTP client groups pattern.
 */
@Configuration
class HttpClientConfig {

    @Bean
    fun captchaClient(
        @Value("\${app.security.captcha.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
        captchaBaseUrl: String
    ): CaptchaClient {
        val restClient = RestClient.builder()
            .baseUrl(captchaBaseUrl)
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(CaptchaClient::class.java)
    }

    @Bean
    fun ssoProviderClient(
        @Value("\${app.security.sso.provider-base-url:https://oauth2.provider.com}")
        ssoBaseUrl: String
    ): SsoProviderClient {
        val restClient = RestClient.builder()
            .baseUrl(ssoBaseUrl)
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(SsoProviderClient::class.java)
    }
}
