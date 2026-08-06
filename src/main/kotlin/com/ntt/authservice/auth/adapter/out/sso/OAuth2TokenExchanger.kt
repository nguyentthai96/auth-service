package com.ntt.authservice.auth.adapter.out.sso

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.SsoProviderTimeoutException
import com.ntt.authservice.shared.exception.SsoTokenInvalidException
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

/**
 * Exchanges OAuth2 authorization codes for user info via provider token endpoints.
 * Supports Google and Microsoft out of the box.
 */
@Component
class OAuth2TokenExchanger(
    private val securityProperties: SecurityProperties
) {
    private val log = LoggerFactory.getLogger(OAuth2TokenExchanger::class.java)
    private val restTemplate = RestTemplate()

    data class ExchangeResult(val sub: String, val email: String?, val name: String?)

    fun exchange(provider: String, code: String, redirectUri: String,
                 clientId: String, clientSecret: String): ExchangeResult {
        val tokenEndpoint = getTokenEndpoint(provider)
        val userInfoEndpoint = getUserInfoEndpoint(provider)

        // 1. Exchange code for access_token
        val tokenResponse = exchangeCode(tokenEndpoint, code, redirectUri, clientId, clientSecret)
        val accessToken = tokenResponse["access_token"] as? String
            ?: throw SsoTokenInvalidException("No access_token in provider response")

        // 2. Fetch user info
        return fetchUserInfo(userInfoEndpoint, accessToken, provider)
    }

    // Provider-specific endpoints
    private fun getTokenEndpoint(provider: String): String = when (provider) {
        "google" -> "https://oauth2.googleapis.com/token"
        "microsoft" -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        else -> throw SsoTokenInvalidException("Unsupported SSO provider: $provider")
    }

    private fun getUserInfoEndpoint(provider: String): String = when (provider) {
        "google" -> "https://openidconnect.googleapis.com/v1/userinfo"
        "microsoft" -> "https://graph.microsoft.com/oidc/userinfo"
        else -> throw SsoTokenInvalidException("Unsupported SSO provider: $provider")
    }

    private fun exchangeCode(endpoint: String, code: String, redirectUri: String,
                              clientId: String, clientSecret: String): Map<*, *> {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", redirectUri)
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        try {
            val response = restTemplate.postForEntity(endpoint, HttpEntity(body, headers), Map::class.java)
            return response.body ?: throw SsoTokenInvalidException("Empty token response")
        } catch (e: Exception) {
            when (e) {
                is SsoTokenInvalidException -> throw e
                else -> {
                    log.error("Token exchange failed: {}", e.message)
                    throw SsoTokenInvalidException("Token exchange failed: ${e.message}")
                }
            }
        }
    }

    private fun fetchUserInfo(endpoint: String, accessToken: String, provider: String): ExchangeResult {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        try {
            val response = restTemplate.exchange(
                endpoint, HttpMethod.GET, HttpEntity<Any>(headers), Map::class.java
            )
            val body = response.body ?: throw SsoTokenInvalidException("Empty userinfo response")
            return ExchangeResult(
                sub = body["sub"] as? String ?: throw SsoTokenInvalidException("Missing 'sub' in userinfo"),
                email = body["email"] as? String,
                name = body["name"] as? String
            )
        } catch (e: Exception) {
            when (e) {
                is SsoTokenInvalidException -> throw e
                else -> {
                    log.error("UserInfo fetch failed for {}: {}", provider, e.message)
                    throw SsoProviderTimeoutException()
                }
            }
        }
    }
}
