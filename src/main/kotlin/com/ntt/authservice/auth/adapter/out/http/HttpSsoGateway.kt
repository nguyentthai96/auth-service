package com.ntt.authservice.auth.adapter.out.http

import com.ntt.authservice.auth.application.port.out.SsoGateway
import com.ntt.authservice.auth.application.port.out.SsoUserInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * SsoGateway implementation using @HttpExchange SsoProviderClient.
 * Replaces SsoAdapter's manual OAuth2 flow.
 */
@Component("httpSsoGateway")
class HttpSsoGateway(
    private val ssoProviderClient: SsoProviderClient
) : SsoGateway {

    private val log = LoggerFactory.getLogger(HttpSsoGateway::class.java)

    override fun exchangeAuthorizationCode(provider: String, code: String): SsoUserInfo {
        // Exchange code for tokens
        val tokenResponse = ssoProviderClient.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code
            )
        )

        // Fetch user info
        val userInfo = ssoProviderClient.getUserInfo()

        log.info("SSO exchange completed for provider={} sub={}", provider, userInfo.sub)

        return SsoUserInfo(
            providerId = userInfo.sub,
            email = userInfo.email,
            name = userInfo.name,
            avatarUrl = userInfo.picture
        )
    }
}
