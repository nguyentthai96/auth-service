package com.ntt.authservice.auth.adapter.`in`.web.dto

/**
 * API response DTO for authentication results.
 * Separates API contract from domain model (AuthToken).
 */
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val userId: Long,
    val username: String,
    val activeDomain: String,
    val roles: List<String>,
    val permissions: List<String>
) {
    companion object {
        /**
         * Convert from domain AuthToken to API DTO.
         */
        fun from(token: com.ntt.authservice.auth.domain.model.AuthToken): AuthResponse = AuthResponse(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            tokenType = token.tokenType,
            expiresIn = token.expiresIn,
            userId = token.userId,
            username = token.username,
            activeDomain = token.activeDomain,
            roles = token.roles,
            permissions = token.permissions
        )
    }
}
