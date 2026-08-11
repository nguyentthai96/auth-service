package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.SessionLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Session policy enforcement — applies configurable max sessions/devices rules.
 * Supports per-role overrides (SUPER_ADMIN = single session, etc.).
 *
 * Strategies:
 * - REVOKE_OLDEST: Auto-revoke oldest session when limit exceeded
 * - REJECT_NEW: Reject the new login attempt
 * - REVOKE_ALL: Revoke all existing sessions before creating new
 */
@Service
class SessionPolicyService(
    private val loginSessionService: LoginSessionService,
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(SessionPolicyService::class.java)

    /**
     * Enforce session limits before creating a new session.
     * Applies per-role overrides when available.
     *
     * @param userId the user ID
     * @param roles user's active roles (for per-role policy lookup)
     * @throws SessionLimitExceededException when REJECT_NEW strategy is active and limit exceeded
     */
    fun enforcePolicy(userId: Long, roles: List<String>) {
        val policy = resolvePolicy(roles)
        val activeCount = loginSessionService.getActiveSessions(userId).size.toLong()

        if (activeCount < policy.maxSessions) {
            return // Within limits
        }

        log.info("Session limit reached userId={} active={} max={} strategy={}",
            userId, activeCount, policy.maxSessions, policy.onExceed)

        when (policy.onExceed) {
            SecurityProperties.SessionProperties.SessionExceedStrategy.REVOKE_OLDEST -> {
                loginSessionService.revokeOldestSession(userId, "POLICY_EXCEED")
            }
            SecurityProperties.SessionProperties.SessionExceedStrategy.REJECT_NEW -> {
                throw SessionLimitExceededException(
                    maxSessions = policy.maxSessions,
                    activeCount = activeCount.toInt()
                )
            }
            SecurityProperties.SessionProperties.SessionExceedStrategy.REVOKE_ALL -> {
                loginSessionService.revokeAllSessions(userId, "POLICY_EXCEED")
            }
        }
    }

    /**
     * Resolve effective session policy — per-role override takes priority.
     * First matching role override wins (ordered by most restrictive).
     */
    private fun resolvePolicy(roles: List<String>): ResolvedPolicy {
        val defaultPolicy = securityProperties.session
        val overrides = defaultPolicy.roleOverrides

        // Find first matching role override
        for (role in roles) {
            val override = overrides[role]
            if (override != null) {
                return ResolvedPolicy(
                    maxSessions = override.maxSessions ?: defaultPolicy.maxSessions,
                    maxDevices = override.maxDevices ?: defaultPolicy.maxDevices,
                    onExceed = override.onExceed ?: defaultPolicy.onExceed
                )
            }
        }

        return ResolvedPolicy(
            maxSessions = defaultPolicy.maxSessions,
            maxDevices = defaultPolicy.maxDevices,
            onExceed = defaultPolicy.onExceed
        )
    }

    private data class ResolvedPolicy(
        val maxSessions: Int,
        val maxDevices: Int,
        val onExceed: SecurityProperties.SessionProperties.SessionExceedStrategy
    )
}
