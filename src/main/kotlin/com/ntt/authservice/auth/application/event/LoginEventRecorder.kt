package com.ntt.authservice.auth.application.event

import com.ntt.authservice.auth.domain.event.UserLoggedInEvent
import com.ntt.authservice.auth.domain.event.UserLoginFailedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Helper service for login event recording.
 * Delegates to EventService.record() — encapsulates topic routing and error handling.
 *
 * FR-004: Helper service for login events.
 * FR-007: Kafka topics — iam.user.logged_in / iam.user.login_failed.
 * FR-010: Fire-and-forget error handling — event recording failure MUST NOT affect login flow.
 * FR-012: EventEnvelope.id UUID — reused from EventService.
 * FR-013: Structured logging — debug on success, warn on failure.
 * FR-014: correlationId threading for end-to-end tracing.
 *
 * Follows TokenEventRecorder pattern EXACTLY: @Component, inject EventService,
 * try/catch all exceptions, log.debug success, log.warn failure.
 */
@Component
class LoginEventRecorder(
    private val eventService: EventService
) {

    private val log = LoggerFactory.getLogger(LoginEventRecorder::class.java)

    /**
     * Record a login success event.
     * Called from LoginHandler.handle() after successful authentication, token generation,
     * session recording, and anonymous session promotion.
     *
     * Fire-and-forget: any exception is caught and logged — login flow continues.
     */
    fun recordLoginSuccess(event: UserLoggedInEvent, userId: Long, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "User",
                aggregateId = userId,
                event = event,
                topic = "iam.user.logged_in",
                partitionKey = userId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Login success event recorded: userId={}, domain={}, isNewDevice={}, correlationId={}",
                userId, event.domainCode, event.isNewDevice, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record login success event: userId={}, error={}",
                userId, e.message, e
            )
        }
    }

    /**
     * Record a login failure event.
     * Called from LoginHandler.handle() catch block when authentication fails.
     *
     * Fire-and-forget: any exception is caught and logged — original exception is rethrown by caller.
     */
    fun recordLoginFailure(event: UserLoginFailedEvent, correlationId: String?) {
        try {
            val aggregateId = event.userId ?: 0L
            eventService.record(
                aggregateType = "User",
                aggregateId = aggregateId,
                event = event,
                topic = "iam.user.login_failed",
                partitionKey = aggregateId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Login failure event recorded: username={}, reason={}, userId={}, correlationId={}",
                event.usernameAttempted, event.failureReason, event.userId, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record login failure event: username={}, reason={}, error={}",
                event.usernameAttempted, event.failureReason, e.message, e
            )
        }
    }
}
