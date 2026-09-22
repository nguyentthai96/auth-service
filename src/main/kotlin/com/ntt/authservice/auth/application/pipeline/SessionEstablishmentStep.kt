package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.SessionPromotionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Step 4: Session establishment — login session, device fingerprint, anonymous promotion.
 * FR-007: Record login session, resolve device fingerprint, promote anonymous session.
 *
 * Source: LoginHandler.kt L158-191
 */
@Component
class SessionEstablishmentStep(
    private val loginSessionService: LoginSessionService,
    private val sessionPromotionService: SessionPromotionService
) : AuthenticationStep {

    private val log = LoggerFactory.getLogger(SessionEstablishmentStep::class.java)

    override val order: Int = 400
    override val name: String = "SessionEstablishment"

    override fun execute(context: AuthenticationContext): StepOutcome {
        val command = context.command
        val user = context.user
            ?: throw IllegalStateException("User not resolved in previous step")

        // Resolve fingerprint
        val resolvedFingerprint = command.deviceFingerprint

        // Record login session — capture isNewDevice (FR-005)
        val loginSession = loginSessionService.recordLogin(
            userId = user.id.value,
            ipAddress = command.ipAddress ?: "unknown",
            userAgent = command.userAgent,
            deviceFingerprint = resolvedFingerprint,
            refreshTokenId = null
        )

        // Anonymous session promotion (best-effort — DD-007)
        val promotionResult = if (!command.anonymousSessionId.isNullOrBlank()) {
            try {
                sessionPromotionService.promoteSession(
                    sessionId = command.anonymousSessionId,
                    userId = user.id.value,
                    anonymousJti = command.anonymousTokenJti ?: ""
                )
            } catch (e: Exception) {
                log.warn("Anonymous session promotion failed for session {}: {}",
                    command.anonymousSessionId, e.message)
                PromotionResult(PromotionResult.Status.FAILED)
            }
        } else null

        log.debug("Session established for user: {} isNewDevice: {}", user.username, loginSession.isNewDevice)
        return StepOutcome.Continue(
            context.copy(
                loginSession = loginSession,
                promotionResult = promotionResult
            )
        )
    }
}
