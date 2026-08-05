package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for SSO provider communication.
 */
interface SsoGateway {
    fun exchangeAuthorizationCode(provider: String, code: String, redirectUri: String): SsoUserInfo
    fun getUserInfo(provider: String, accessToken: String): SsoUserInfo
}

/**
 * SSO user information returned from provider.
 */
data class SsoUserInfo(
    val externalId: String,
    val email: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val provider: String
)
