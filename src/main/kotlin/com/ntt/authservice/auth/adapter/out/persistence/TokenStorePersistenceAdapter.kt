package com.ntt.authservice.auth.adapter.out.persistence

import com.ntt.authservice.auth.application.TokenBlacklistCacheService
import com.ntt.authservice.auth.application.port.out.RefreshTokenInfo
import com.ntt.authservice.auth.application.port.out.TokenStore
import com.ntt.authservice.rbac.adapter.out.persistence.entity.RefreshTokenEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.TokenBlacklistEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.RefreshTokenRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * JPA adapter implementing TokenStore.
 * Write-through: DB persist (source of truth) + cache population (FR-002, FR-016).
 */
@Component
class TokenStorePersistenceAdapter(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val tokenBlacklistCacheService: TokenBlacklistCacheService
) : TokenStore {

    private val log = LoggerFactory.getLogger(TokenStorePersistenceAdapter::class.java)

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

    override fun blacklistToken(jti: String, userId: Long, reason: String, expiresAt: Instant) {
        // DB persist (source of truth)
        val entry = TokenBlacklistEntity().apply {
            this.tokenJti = jti
            this.userId = userId
            this.reason = reason
            this.expiresAt = expiresAt
            this.revokedAt = Instant.now()
        }
        tokenBlacklistRepository.save(entry)

        // Write-through to cache (FR-002, FR-016)
        // Cache write failure does NOT roll back DB — DB is source of truth.
        try {
            val remainingSeconds = Duration.between(Instant.now(), expiresAt).seconds
            if (remainingSeconds > 0) {
                tokenBlacklistCacheService.addToBlacklist(jti, remainingSeconds)
            }
        } catch (e: Exception) {
            log.warn("Failed to write-through blacklist to cache: jti={}, error={}", jti, e.message)
        }
    }
}
