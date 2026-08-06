package com.ntt.authservice.auth.application.event

import org.springframework.context.ApplicationEvent
import java.time.Instant

/**
 * Event published when a user is locked due to exceeding MFA rate limit.
 *
 * Consumers can listen via @EventListener for alerting (email, Slack, etc).
 * Default behavior: log only. Extensible without modifying core logic (FR-010).
 */
class RateLimitExceededEvent(
    val userId: Long,
    val lockType: String,
    val attemptCount: Int,
    val lockDurationSeconds: Long,
    val timestamp: Instant = Instant.now()
) : ApplicationEvent(userId)
