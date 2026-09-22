package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.event.LoginEventRecorder
import com.ntt.authservice.auth.application.pipeline.AuthenticationPipeline
import com.ntt.authservice.auth.domain.event.LoginFailureReason
import com.ntt.authservice.auth.domain.event.UserLoggedInEvent
import com.ntt.authservice.auth.domain.event.UserLoginFailedEvent
import com.ntt.authservice.shared.exception.*
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Login handler — delegates to AuthenticationPipeline (Chain of Responsibility).
 * FR-001: Reduced from 17 dependencies to 3.
 *
 * Responsibilities after refactoring:
 * 1. Delegate authentication flow to pipeline
 * 2. Record success/failure domain events (cross-cutting)
 * 3. Map exceptions to failure reasons
 */
@Component
class LoginHandler(
    private val authenticationPipeline: AuthenticationPipeline,
    private val loginEventRecorder: LoginEventRecorder
) : CommandHandler<LoginCommand, LoginResult> {

    private val log = LoggerFactory.getLogger(LoginHandler::class.java)

    override fun commandType(): Class<LoginCommand> = LoginCommand::class.java

    @Transactional
    override fun handle(command: LoginCommand): LoginResult {
        try {
            val result = authenticationPipeline.execute(command)

            // Record login success event (fire-and-forget — FR-005)
            if (result is LoginResult.Success) {
                loginEventRecorder.recordLoginSuccess(
                    event = UserLoggedInEvent(
                        userId = 0, // Event recorder will resolve from context
                        username = command.username,
                        domainCode = command.domainCode ?: "",
                        domainId = null,
                        loginMethod = "PASSWORD",
                        mfaBypassed = false,
                        mfaMethod = null,
                        isNewDevice = false,
                        ipAddress = command.ipAddress,
                        userAgent = command.userAgent,
                        deviceFingerprint = command.deviceFingerprint,
                        sessionPromotionStatus = result.promotionResult?.status?.name
                    ),
                    userId = 0,
                    correlationId = command.correlationId
                )
            }

            log.info("User logged in: {} ip: {}", command.username, command.ipAddress)
            return result
        } catch (e: AuthException) {
            // Record login failure event (fire-and-forget — FR-006)
            loginEventRecorder.recordLoginFailure(
                UserLoginFailedEvent(
                    usernameAttempted = command.username,
                    userId = null,
                    failureReason = mapToFailureReason(e),
                    ipAddress = command.ipAddress,
                    userAgent = command.userAgent,
                    deviceFingerprint = command.deviceFingerprint
                ),
                command.correlationId
            )
            throw e
        }
    }

    /**
     * Map AuthException subtypes to LoginFailureReason enum values.
     * Handler-specific mapping — keeps LoginEventRecorder generic and reusable.
     */
    private fun mapToFailureReason(exception: AuthException): LoginFailureReason = when (exception) {
        is InvalidCredentialsException -> LoginFailureReason.INVALID_CREDENTIALS
        is AccountLockedException -> LoginFailureReason.ACCOUNT_LOCKED
        is AccountLockedPermanentException -> LoginFailureReason.ACCOUNT_LOCKED
        is CaptchaRequiredException -> LoginFailureReason.CAPTCHA_REQUIRED
        is CaptchaFailedException -> LoginFailureReason.CAPTCHA_FAILED
        is PasswordExpiredException -> LoginFailureReason.PASSWORD_EXPIRED
        is RateLimitExceededException -> LoginFailureReason.RATE_LIMITED
        else -> LoginFailureReason.UNKNOWN
    }
}
