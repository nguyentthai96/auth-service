package com.ntt.authservice.auth.adapter.out.http

import com.ntt.authservice.auth.application.port.out.SsoGateway
import com.ntt.authservice.auth.application.port.out.SsoUserInfo
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * SsoGateway implementation using @HttpExchange SsoProviderClient (FR-019).
 * Circuit breaker protects against provider unavailability.
 */
@Component("httpSsoGateway")
class HttpSsoGateway(
    private val ssoProviderClient: SsoProviderClient
) : SsoGateway {

    private val log = LoggerFactory.getLogger(HttpSsoGateway::class.java)

    @CircuitBreaker(name = "ssoProvider", fallbackMethod = "exchangeCodeFallback")
    override fun exchangeAuthorizationCode(provider: String, code: String, redirectUri: String): SsoUserInfo {
        // Exchange code for tokens
        val tokenResponse = ssoProviderClient.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri
            )
        )

        // Fetch user info
        val userInfo = ssoProviderClient.getUserInfo()

        log.info("SSO exchange completed for provider={} sub={}", provider, userInfo.sub)

        return SsoUserInfo(
            externalId = userInfo.sub,
            email = userInfo.email ?: "",
            name = userInfo.name,
            avatarUrl = userInfo.picture,
            provider = provider
        )
    }

    @CircuitBreaker(name = "ssoProvider", fallbackMethod = "getUserInfoFallback")
    override fun getUserInfo(provider: String, accessToken: String): SsoUserInfo {
        val userInfo = ssoProviderClient.getUserInfo()
        return SsoUserInfo(
            externalId = userInfo.sub,
            email = userInfo.email ?: "",
            name = userInfo.name,
            avatarUrl = userInfo.picture,
            provider = provider
        )
    }

    /** Circuit breaker fallback for exchangeAuthorizationCode. */
    @Suppress("unused")
    private fun exchangeCodeFallback(provider: String, code: String, redirectUri: String, ex: Throwable): SsoUserInfo {
        log.error("SSO provider circuit breaker OPEN for provider={}: {}", provider, ex.message)
        throw com.ntt.authservice.shared.exception.AuthException(
            authError = com.ntt.authservice.shared.exception.AuthErrorCode.SSO_TOKEN_INVALID,
            message = "SSO provider is temporarily unavailable. Please try again later.",
            httpStatus = org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
        )
    }

    /** Circuit breaker fallback for getUserInfo. */
    @Suppress("unused")
    private fun getUserInfoFallback(provider: String, accessToken: String, ex: Throwable): SsoUserInfo {
        log.error("SSO provider circuit breaker OPEN for getUserInfo provider={}: {}", provider, ex.message)
        throw com.ntt.authservice.shared.exception.AuthException(
            authError = com.ntt.authservice.shared.exception.AuthErrorCode.SSO_TOKEN_INVALID,
            message = "SSO provider is temporarily unavailable. Please try again later.",
            httpStatus = org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
        )
    }
}
