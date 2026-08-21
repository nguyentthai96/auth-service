package com.ntt.authservice.shared.config

import com.ntt.authservice.auth.adapter.out.http.CaptchaClient
import com.ntt.authservice.auth.adapter.out.http.SsoProviderClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

/**
 * Configuration for @HttpExchange declarative clients (FR-019).
 * Configurable timeout per client with circuit breaker support.
 *
 * Timeout defaults:
 * - Connect timeout: 5s (configurable via app.http.*.connect-timeout-ms)
 * - Read timeout: 10s (configurable via app.http.*.read-timeout-ms)
 *
 * Circuit breaker is configured via Resilience4j properties (application.yml):
 * - resilience4j.circuitbreaker.instances.ssoProvider.*
 * - resilience4j.circuitbreaker.instances.captchaProvider.*
 */
@Configuration
class HttpClientConfig {

    @Bean
    fun captchaClient(
        @Value("\${app.security.captcha.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
        captchaBaseUrl: String,
        @Value("\${app.http.captcha.connect-timeout-ms:5000}") connectTimeout: Int,
        @Value("\${app.http.captcha.read-timeout-ms:10000}") readTimeout: Int
    ): CaptchaClient {
        val requestFactory = createTimeoutFactory(connectTimeout, readTimeout)
        val restClient = RestClient.builder()
            .baseUrl(captchaBaseUrl)
            .requestFactory(requestFactory)
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(CaptchaClient::class.java)
    }

    @Bean
    fun ssoProviderClient(
        @Value("\${app.security.sso.provider-base-url:https://oauth2.provider.com}")
        ssoBaseUrl: String,
        @Value("\${app.http.sso.connect-timeout-ms:5000}") connectTimeout: Int,
        @Value("\${app.http.sso.read-timeout-ms:10000}") readTimeout: Int
    ): SsoProviderClient {
        val requestFactory = createTimeoutFactory(connectTimeout, readTimeout)
        val restClient = RestClient.builder()
            .baseUrl(ssoBaseUrl)
            .requestFactory(requestFactory)
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(SsoProviderClient::class.java)
    }

    /**
     * Create a request factory with configurable timeouts (FR-019).
     * Circuit breaker annotations are applied at the gateway level (@CircuitBreaker).
     */
    private fun createTimeoutFactory(connectTimeoutMs: Int, readTimeoutMs: Int): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs.toLong()))
            setReadTimeout(Duration.ofMillis(readTimeoutMs.toLong()))
        }
    }
}
