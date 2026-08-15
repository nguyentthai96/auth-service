package com.ntt.authservice.auth.adapter.out.cipher

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.adapter.out.persistence.repository.CipherKeySessionJpaRepository
import com.ntt.authservice.shared.exception.CipherKeyExpiredException
import com.ntt.basecore.autoconfigure.security.cipher.key.CipherKeySession
import com.ntt.basecore.autoconfigure.security.cipher.key.CipherKeySessionResolver
import com.ntt.basecore.autoconfigure.security.cipher.model.CipherHeaders
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis L1 + JPA L2 key session resolver.
 *
 * Lookup chain:
 * 1. Extract X-Key-ID from request header
 * 2. Redis lookup (primary — fast, TTL-based)
 * 3. JPA DB lookup (fallback — backup/audit)
 * 4. Back-fill Redis on JPA hit (heal cache misses)
 *
 * Validates session expiry and isActive status.
 */
@Service
class RedisCipherKeySessionResolver(
    private val redisTemplate: StringRedisTemplate,
    private val jpaRepository: CipherKeySessionJpaRepository,
    private val objectMapper: ObjectMapper
) : CipherKeySessionResolver {

    private val log = LoggerFactory.getLogger(RedisCipherKeySessionResolver::class.java)

    companion object {
        private const val REDIS_KEY_PREFIX = "cipher:session:"
        private const val REDIS_TTL_HOURS = 24L
    }

    override fun resolveSession(request: HttpServletRequest): CipherKeySession {
        val keyId = request.getHeader(CipherHeaders.X_KEY_ID)
            ?: throw com.ntt.basecore.autoconfigure.security.cipher.model.CipherKeyNotFoundException(
                "Missing X-Key-ID header"
            )

        // L1: Redis lookup
        val redisKey = "$REDIS_KEY_PREFIX$keyId"
        val cached = redisTemplate.opsForValue().get(redisKey)
        if (cached != null) {
            val session = objectMapper.readValue(cached, CipherKeySession::class.java)
            if (session.isExpired) {
                redisTemplate.delete(redisKey)
                throw CipherKeyExpiredException(keyId)
            }
            return session
        }

        // L2: JPA fallback
        val entity = jpaRepository.findById(keyId).orElseThrow {
            com.ntt.basecore.autoconfigure.security.cipher.model.CipherKeyNotFoundException(
                "Key session not found: $keyId"
            )
        }

        if (!entity.isActive) {
            throw CipherKeyExpiredException(keyId)
        }

        val session = CipherKeySession(
            keyId = entity.keyId,
            userId = entity.userId,
            deviceId = entity.deviceId,
            platform = entity.platform,
            algorithmId = entity.algorithmId,
            clientToServerKey = entity.clientToServerKey,
            serverToClientKey = entity.serverToClientKey,
            keyVersion = entity.keyVersion,
            appVersion = entity.appVersion,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt,
            isActive = entity.isActive
        )

        if (session.isExpired) {
            throw CipherKeyExpiredException(keyId)
        }

        // Back-fill Redis (heal cache miss)
        try {
            val json = objectMapper.writeValueAsString(session)
            redisTemplate.opsForValue().set(redisKey, json, REDIS_TTL_HOURS, TimeUnit.HOURS)
            log.debug("Back-filled Redis for keyId={}", keyId)
        } catch (e: Exception) {
            log.warn("Failed to back-fill Redis for keyId={}: {}", keyId, e.message)
        }

        return session
    }
}
