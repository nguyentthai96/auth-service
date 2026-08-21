package com.ntt.authservice.auth.application

import com.ntt.authservice.rbac.adapter.out.persistence.entity.TokenBlacklistEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Orchestrates anonymous session promotion during login/register.
 * Flow: acquireLock → verifySession → transferData → blacklistToken → deleteSession → releaseLock.
 *
 * Promotion is best-effort: login/register succeeds even if promotion fails (DD-007).
 * Uses distributed lock with UUID ownership to prevent concurrent promotion of the same session (FR-010).
 * Lock release uses Lua script for ownership verification — prevents cross-process lock release (FR-004).
 */
@Service
class SessionPromotionService(
    private val redisTemplate: StringRedisTemplate,
    private val anonymousSessionDataService: AnonymousSessionDataService,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry,
    private val safeLockReleaseScript: DefaultRedisScript<Long>
) {

    private val log = LoggerFactory.getLogger(SessionPromotionService::class.java)

    companion object {
        private const val SESSION_PREFIX = "anon:session:"
        private const val LOCK_PREFIX = "anon:lock:"
        private const val LOCK_TTL_SECONDS = 30L
    }

    /**
     * Promote an anonymous session to an authenticated user.
     * Returns PromotionResult indicating the outcome.
     *
     * @param sessionId the anonymous session ID
     * @param userId the authenticated user's ID
     * @param anonymousJti the JTI of the anonymous token to blacklist
     */
    fun promoteSession(sessionId: String, userId: Long, anonymousJti: String): PromotionResult {
        val sample = Timer.start(meterRegistry)

        // Step 1: Acquire distributed lock (returns ownerUUID on success, null on failure)
        val ownerUUID = acquireLock(sessionId)
        if (ownerUUID == null) {
            log.warn("Promotion lock acquisition failed for session={} — concurrent promotion detected", sessionId)
            meterRegistry.counter("auth.anonymous.sessions.promoted", "status", "CONFLICT").increment()
            return PromotionResult(status = PromotionResult.Status.CONFLICT)
        }

        try {
            // Step 2: Verify session exists
            if (!anonymousSessionDataService.verifySessionExists(sessionId)) {
                log.warn("Anonymous session not found during promotion: session={}", sessionId)
                meterRegistry.counter("auth.anonymous.sessions.promoted", "status", "FAILED").increment()
                return PromotionResult(status = PromotionResult.Status.FAILED)
            }

            // Step 3: Transfer data
            val transferResult = try {
                anonymousSessionDataService.transferData(sessionId, userId)
            } catch (ex: Exception) {
                log.warn("Data transfer failed during promotion session={}: {}", sessionId, ex.message)
                null
            }

            // Step 4: Blacklist anonymous token
            try {
                val blacklistEntry = TokenBlacklistEntity().apply {
                    tokenJti = anonymousJti
                    this.userId = userId
                    reason = "PROMOTION"
                    expiresAt = Instant.now().plusSeconds(securityProperties.anonymous.tokenTtlSeconds)
                    revokedAt = Instant.now()
                }
                tokenBlacklistRepository.save(blacklistEntry)
            } catch (ex: Exception) {
                log.warn("Failed to blacklist anonymous token jti={} during promotion: {}", anonymousJti, ex.message)
            }

            // Step 5: Delete session and all data
            try {
                redisTemplate.delete("$SESSION_PREFIX$sessionId")
                anonymousSessionDataService.deleteAllSessionData(sessionId)
            } catch (ex: Exception) {
                log.warn("Failed to cleanup anonymous session={} during promotion: {}", sessionId, ex.message)
            }

            // Determine result status
            val status = when {
                transferResult == null -> PromotionResult.Status.PARTIAL
                transferResult.partial -> PromotionResult.Status.PARTIAL
                else -> PromotionResult.Status.SUCCESS
            }

            // Metrics: promotion completed
            meterRegistry.counter("auth.anonymous.sessions.promoted", "status", status.name).increment()
            sample.stop(meterRegistry.timer("auth.anonymous.promotion.duration"))

            log.info(
                "Anonymous session promotion completed: session={} userId={} status={} items={}",
                sessionId, userId, status, transferResult?.itemCount ?: 0
            )

            return PromotionResult(
                status = status,
                itemCount = transferResult?.itemCount ?: 0,
                namespaces = transferResult?.namespaces ?: emptyList()
            )
        } finally {
            // Step 6: Release lock (with ownership verification via Lua)
            releaseLock(sessionId, ownerUUID)
        }
    }

    /**
     * Acquire a distributed lock for session promotion using SETNX with UUID ownership + TTL.
     * Returns the ownerUUID on success, null on failure.
     */
    private fun acquireLock(sessionId: String): String? {
        val lockKey = "$LOCK_PREFIX$sessionId"
        val ownerUUID = UUID.randomUUID().toString()
        return try {
            val acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey, ownerUUID, Duration.ofSeconds(LOCK_TTL_SECONDS)
            ) == true
            if (acquired) ownerUUID else null
        } catch (ex: Exception) {
            log.error("Failed to acquire promotion lock for session={}: {}", sessionId, ex.message)
            null
        }
    }

    /**
     * Release the distributed lock using Lua script for ownership verification.
     * Only deletes the lock if the stored value matches the ownerUUID — prevents cross-process lock release.
     */
    private fun releaseLock(sessionId: String, ownerUUID: String) {
        val lockKey = "$LOCK_PREFIX$sessionId"
        try {
            val released = redisTemplate.execute(
                safeLockReleaseScript, listOf(lockKey), ownerUUID
            )
            if (released == 0L) {
                log.warn("Lock for session={} owned by different process — skipped release", sessionId)
            }
        } catch (ex: Exception) {
            log.warn("Failed to release lock for session={} via Lua — will auto-expire in {}s", sessionId, LOCK_TTL_SECONDS)
        }
    }
}
