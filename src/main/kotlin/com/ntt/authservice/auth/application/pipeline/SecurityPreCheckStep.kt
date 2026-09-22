package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.application.AccountLockoutService
import com.ntt.authservice.auth.application.CaptchaStrategyRegistry
import com.ntt.authservice.auth.application.LoginRateLimitService
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.CaptchaFailedException
import com.ntt.authservice.shared.exception.CaptchaRequiredException
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Step 1: Security pre-checks before credential verification.
 * FR-004: User lookup, lockout validation, CAPTCHA check, rate limiting.
 *
 * Source: LoginHandler.kt L74-100
 */
@Component
class SecurityPreCheckStep(
    private val userPort: UserPort,
    private val accountLockoutService: AccountLockoutService,
    private val captchaStrategyRegistry: CaptchaStrategyRegistry,
    private val loginRateLimitService: LoginRateLimitService,
    private val securityProperties: SecurityProperties
) : AuthenticationStep {

    private val log = LoggerFactory.getLogger(SecurityPreCheckStep::class.java)

    override val order: Int = 100
    override val name: String = "SecurityPreCheck"

    override fun execute(context: AuthenticationContext): StepOutcome {
        val command = context.command

        // Resolve user by username
        val user = userPort.findByUsernameAndActive(command.username)
            ?: run {
                loginRateLimitService.recordFailedAttempt(
                    ip = command.ipAddress ?: "unknown",
                    username = command.username,
                    deviceFingerprint = command.deviceFingerprint
                )
                throw InvalidCredentialsException()
            }

        // Check account lock status (FR-010)
        accountLockoutService.validateLockoutStatus(user)

        // CAPTCHA check when failed login threshold exceeded (FR-014)
        val lockoutFailedCount = accountLockoutService.getFailedAttemptCount(user.id.value)
        val maxAttempts = securityProperties.lockout.maxFailedAttempts
        if (lockoutFailedCount >= maxAttempts - 1) {
            val captchaToken = command.captchaToken
            if (captchaToken.isNullOrBlank()) {
                throw CaptchaRequiredException()
            }
            if (!captchaStrategyRegistry.verify(captchaToken, command.captchaType)) {
                throw CaptchaFailedException()
            }
        }

        log.debug("Security pre-check passed for user: {}", command.username)
        return StepOutcome.Continue(context.copy(user = user))
    }
}
