package com.ntt.authservice.auth.application.event

import com.ntt.authservice.auth.domain.event.TokenIssuedEvent
import com.ntt.authservice.auth.domain.event.TokenRevokedEvent
import com.ntt.authservice.auth.domain.event.TokenValidationFailedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Helper service for token lifecycle event recording.
 * Delegates to EventService.record() — encapsulates topic routing and error handling.
 *
 * Error handling (FR-015): All exceptions from EventService.record() are caught and logged.
 * Token issuance/revocation MUST NOT fail due to event recording failure.
 *
 * Follows the helper service pattern — keeps TokenGenerator focused on token logic.
 */
@Component
class TokenEventRecorder(
    private val eventService: EventService
) {

    private val log = LoggerFactory.getLogger(TokenEventRecorder::class.java)

    /**
     * Record a token issuance event.
     * Called from TokenGenerator.generateAuthResponse() after successful token generation.
     */
    fun recordIssuance(event: TokenIssuedEvent, userId: Long, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "User",
                aggregateId = userId,
                event = event,
                topic = "iam.token.issued",
                partitionKey = userId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Token issuance event recorded: userId={}, context={}, jti={}, correlationId={}",
                userId, event.issuanceContext, event.accessTokenJti, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record token issuance event: userId={}, context={}, error={}",
                userId, event.issuanceContext, e.message, e
            )
        }
    }

    /**
     * Record a token revocation event.
     * Called from RefreshTokenHandler (ROTATION) and AuthService (LOGOUT, BULK_REVOKE).
     */
    fun recordRevocation(event: TokenRevokedEvent, userId: Long, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "User",
                aggregateId = userId,
                event = event,
                topic = "iam.token.revoked",
                partitionKey = userId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Token revocation event recorded: userId={}, type={}, count={}, correlationId={}",
                userId, event.revocationType, event.revokedCount, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record token revocation event: userId={}, type={}, error={}",
                userId, event.revocationType, e.message, e
            )
        }
    }

    /**
     * Record a token validation failure event (FR-012).
     * Called from JwtAuthFilter on suspicious validation failures (blacklisted, signature, claim mismatch).
     * Uses aggregateId=0L because validation failures have no authenticated user context.
     *
     * Error handling: failure to record does NOT affect the filter chain — fail-safe.
     */
    fun recordValidationFailure(event: TokenValidationFailedEvent, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "Token",
                aggregateId = 0L,
                event = event,
                topic = "iam.token.validation-failed",
                partitionKey = event.tokenJti ?: "unknown",
                correlationId = correlationId
            )
            log.debug(
                "Token validation failure event recorded: reason={}, jti={}, validator={}, correlationId={}",
                event.reason, event.tokenJti, event.validatorName, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record token validation failure event: reason={}, jti={}, error={}",
                event.reason, event.tokenJti, e.message, e
            )
        }
    }
}
