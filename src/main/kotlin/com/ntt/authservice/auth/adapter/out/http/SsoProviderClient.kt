package com.ntt.authservice.auth.adapter.out.http

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.PostExchange

/**
 * Declarative HTTP client for SSO provider token exchange.
 * Spring Boot 4.x @HttpExchange pattern.
 *
 * Config in application.yml:
 * spring.http.client.service.sso-provider.base-url: configured per-provider
 */
@HttpExchange
interface SsoProviderClient {

    @PostExchange("/oauth2/token")
    fun exchangeToken(@RequestBody request: Map<String, String>): SsoTokenResponse

    @GetExchange("/userinfo")
    fun getUserInfo(): SsoUserInfoResponse
}

data class SsoTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String? = null,
    val idToken: String? = null
)

data class SsoUserInfoResponse(
    val sub: String,
    val email: String?,
    val name: String?,
    val picture: String?
)
