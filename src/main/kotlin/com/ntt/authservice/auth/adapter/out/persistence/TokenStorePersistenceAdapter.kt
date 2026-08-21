package com.ntt.authservice.auth.adapter.out.persistence

import com.ntt.authservice.auth.application.port.out.RefreshTokenInfo
import com.ntt.authservice.auth.application.port.out.TokenStore
import com.ntt.authservice.rbac.adapter.out.persistence.entity.RefreshTokenEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.RefreshTokenRepository
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * JPA adapter implementing TokenStore.
 */
@Component
class TokenStorePersistenceAdapter(
    private val refreshTokenRepository: RefreshTokenRepository
) : TokenStore {

    override fun saveRefreshToken(userId: Long, tokenHash: String, expiresAt: Instant) {
        val entity = RefreshTokenEntity().apply {
            this.userId = userId
            this.tokenHash = tokenHash
            this.expiresAt = expiresAt
        }
        refreshTokenRepository.save(entity)
    }

    override fun findValidRefreshToken(tokenHash: String): RefreshTokenInfo? {
        return refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)?.let {
            RefreshTokenInfo(
                userId = it.userId,
                tokenHash = it.tokenHash,
                expiresAt = it.expiresAt,
                revoked = it.revoked
            )
        }
    }

    override fun revokeToken(tokenHash: String) {
        val entity = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
        if (entity != null) {
            entity.revoked = true
            refreshTokenRepository.save(entity)
        }
    }

    override fun revokeAllForUser(userId: Long): Int {
        return refreshTokenRepository.revokeAllByUserId(userId)
    }
}
