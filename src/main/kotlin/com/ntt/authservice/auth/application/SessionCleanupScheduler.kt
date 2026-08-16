package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.shared.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Scheduled job — auto-expire inactive sessions (FR-008).
 *
 * Runs on configured cron schedule (default: every 5 minutes).
 * Finds sessions where last_activity_at < (now - inactivity_timeout)
 * and marks them as expired.
 */
@Component
class SessionCleanupScheduler(
    private val loginSessionRepository: LoginSessionRepository,
    private val securityProperties: SecurityProperties
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
}
