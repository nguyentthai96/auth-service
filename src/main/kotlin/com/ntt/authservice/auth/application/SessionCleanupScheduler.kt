package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import com.ntt.authservice.shared.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Scheduled cleanup jobs for auth resources:
 * - Inactive login sessions (FR-008, existing)
 * - Expired token blacklist entries (FR-013, anonymous-login-optimization v2)
 *
 * Each cleanup method runs on an independent cron schedule.
 */
@Component
class SessionCleanupScheduler(
    private val loginSessionRepository: LoginSessionRepository,
    private val securityProperties: SecurityProperties,
    private val tokenBlacklistRepository: TokenBlacklistRepository
) {

    private val log = LoggerFactory.getLogger(SessionCleanupScheduler::class.java)

    /**
     * Clean up inactive sessions based on configured timeout.
     * Uses SpEL to read cron from SecurityProperties.
     */
    @Scheduled(cron = "\${app.security.session.cleanup-cron-expression:0 */5 * * * *}")
    @Transactional
    fun cleanupInactiveSessions() {
        val timeoutMinutes = securityProperties.session.inactivityTimeoutMinutes
        val cutoff = Instant.now().minusSeconds(timeoutMinutes * 60)

        val inactiveSessions = loginSessionRepository.findBySessionActiveTrueAndLastActivityAtBefore(cutoff)

        if (inactiveSessions.isEmpty()) {
            log.debug("SESSION_CLEANUP No inactive sessions found (cutoff={})", cutoff)
            return
        }

        val now = Instant.now()
        inactiveSessions.forEach { session ->
            session.sessionActive = false
            session.revokedAt = now
            session.revokeReason = "INACTIVITY"
        }
        loginSessionRepository.saveAll(inactiveSessions)

        log.info("SESSION_CLEANUP Expired {} inactive sessions (timeout={}min cutoff={})",
            inactiveSessions.size, timeoutMinutes, cutoff)
    }

    /**
     * Clean up expired token blacklist entries (FR-013).
     * Removes entries where expires_at < NOW() to prevent unbounded table growth.
     * Default: runs every 6 hours (configurable via app.security.anonymous.blacklist-cleanup-cron).
     */
    @Scheduled(cron = "\${app.security.anonymous.blacklist-cleanup-cron:0 0 */6 * * *}")
    @Transactional
    fun cleanupExpiredBlacklistEntries() {
        val cutoff = Instant.now()
        try {
            val deletedCount = tokenBlacklistRepository.deleteByExpiresAtBefore(cutoff)
            if (deletedCount > 0) {
                log.info("TOKEN_BLACKLIST_CLEANUP Deleted {} expired entries (cutoff={})",
                    deletedCount, cutoff)
            } else {
                log.debug("TOKEN_BLACKLIST_CLEANUP No expired entries found (cutoff={})", cutoff)
            }
        } catch (ex: Exception) {
            log.error("TOKEN_BLACKLIST_CLEANUP Failed to cleanup expired entries", ex)
        }
    }
}
