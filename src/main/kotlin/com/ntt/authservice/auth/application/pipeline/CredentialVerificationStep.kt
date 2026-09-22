package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.application.AccountLockoutService
import com.ntt.authservice.auth.application.LoginRateLimitService
import com.ntt.authservice.auth.application.PasswordUpgradeService
import com.ntt.authservice.auth.application.command.TokenGenerator
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Step 2: Credential verification — password check + hash upgrade.
 * FR-005: Password verify, record failed attempts, reset on success, hash migration.
 *
 * Source: LoginHandler.kt L102-131
 */
@Component
class CredentialVerificationStep(
    private val tokenGenerator: TokenGenerator,
    private val passwordUpgradeService: PasswordUpgradeService,
    private val userPort: UserPort,
    private val accountLockoutService: AccountLockoutService,
    private val loginRateLimitService: LoginRateLimitService
) : AuthenticationStep {

    private val log = LoggerFactory.getLogger(CredentialVerificationStep::class.java)

    override val order: Int = 200
    override val name: String = "CredentialVerification"

    override fun execute(context: AuthenticationContext): StepOutcome {
        val command = context.command
        val user = context.user
            ?: throw IllegalStateException("User not resolved in previous step")

        // Validate password
        if (!tokenGenerator.matchesPassword(command.password, user.passwordHash.value)) {
            accountLockoutService.recordFailedAttempt(user, command.ipAddress)
            loginRateLimitService.recordFailedAttempt(
                ip = command.ipAddress ?: "unknown",
                username = command.username,
                deviceFingerprint = command.deviceFingerprint
            )
            throw InvalidCredentialsException()
        }

        // Reset failed login count and lockout state on success
        user.resetFailedLogins()
        accountLockoutService.resetOnSuccess(user.id.value)

        // Upgrade password hash if using legacy algorithm (transparent migration)
        passwordUpgradeService.upgradeIfNeeded(user, command.password)
        userPort.save(user)

        // Reset rate limit counters on successful auth
        loginRateLimitService.resetOnSuccess(
            ip = command.ipAddress ?: "unknown",
            username = command.username,
            deviceFingerprint = command.deviceFingerprint
        )

        log.debug("Credential verification passed for user: {}", user.username)
        return StepOutcome.Continue(context)
    }
}
