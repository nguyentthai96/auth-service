package com.ntt.authservice.auth.application.port.out

import java.time.Instant

/**
 * Outbound port for refresh token storage (Redis or DB).
 */
interface TokenStore {
    fun saveRefreshToken(userId: Long, tokenHash: String, expiresAt: Instant)
    fun findValidRefreshToken(tokenHash: String): RefreshTokenInfo?
    fun revokeToken(tokenHash: String)
    fun revokeAllForUser(userId: Long): Int
}

/**
 * Refresh token info from storage — decoupled from persistence entity.
 */
data class RefreshTokenInfo(
    val userId: Long,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean = false
)
