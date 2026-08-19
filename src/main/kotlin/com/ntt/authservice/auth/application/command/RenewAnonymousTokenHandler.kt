package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.AnonymousSessionResult
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.rbac.adapter.out.persistence.entity.TokenBlacklistEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousMaxRenewalsException
import com.ntt.authservice.shared.exception.AnonymousSessionExpiredException
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Handler for anonymous token renewal.
 * Follows RefreshTokenHandler pattern.
 *
 * Flow: parseToken → verify session → check renewal count → generate new token →
 *       blacklist old JTI → increment renewalCount → refresh TTL → return result.
 */
@Component
class RenewAnonymousTokenHandler(
    private val jwtService: JwtService,
    private val redisTemplate: StringRedisTemplate,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val securityProperties: SecurityProperties
) : CommandHandler<RenewAnonymousTokenCommand, AnonymousSessionResult> {

    private val log = LoggerFactory.getLogger(RenewAnonymousTokenHandler::class.java)

    companion object {
        private const val SESSION_PREFIX = "anon:session:"
        private const val SENTINEL_USER_ID = 0L
    }

    override fun commandType(): Class<RenewAnonymousTokenCommand> = RenewAnonymousTokenCommand::class.java

    override fun handle(command: RenewAnonymousTokenCommand): AnonymousSessionResult {
        // Step 1: Parse and validate the current anonymous token
        val claims = jwtService.parseAnonymousToken(command.currentToken)
        val sessionId = claims.subject
        val oldJti = claims.id

        // Step 2: Verify session exists in Redis
        val sessionKey = "$SESSION_PREFIX$sessionId"
        if (redisTemplate.hasKey(sessionKey) != true) {
            throw AnonymousSessionExpiredException(sessionId)
        }

        // Step 3: Check renewal count
        val renewalCountStr = redisTemplate.opsForHash<String, String>().get(sessionKey, "renewalCount") ?: "0"
        val renewalCount = renewalCountStr.toIntOrNull() ?: 0
        val maxRenewals = securityProperties.anonymous.maxRenewals

        if (renewalCount >= maxRenewals) {
            throw AnonymousMaxRenewalsException(maxRenewals)
        }

        // Step 4: Generate new anonymous token (same sessionId)
        val newToken = jwtService.generateAnonymousToken(sessionId)

        // Step 5: Blacklist old JTI (userId=0 sentinel — DD-004)
        try {
            val blacklistEntry = TokenBlacklistEntity().apply {
                tokenJti = oldJti
                userId = SENTINEL_USER_ID
                reason = "RENEWAL"
                expiresAt = Instant.now().plusSeconds(securityProperties.anonymous.tokenTtlSeconds)
                revokedAt = Instant.now()
            }
            tokenBlacklistRepository.save(blacklistEntry)
        } catch (ex: Exception) {
            log.warn("Failed to blacklist old anonymous token jti={} during renewal: {}", oldJti, ex.message)
        }

        // Step 6: Increment renewal count and refresh session TTL
        val ops = redisTemplate.opsForHash<String, String>()
        ops.put(sessionKey, "renewalCount", (renewalCount + 1).toString())
        redisTemplate.expire(sessionKey, Duration.ofSeconds(securityProperties.anonymous.sessionTtlSeconds))

        log.info("Anonymous token renewed: sessionId={} renewalCount={}", sessionId, renewalCount + 1)

        return AnonymousSessionResult(
            token = newToken,
            sessionId = sessionId,
            expiresIn = securityProperties.anonymous.tokenTtlSeconds
        )
    }
}
