package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Login handler — extracted from AuthService.login().
 * Implements CQRS CommandHandler for clean separation of concerns.
 */
@Component
class LoginHandler(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val tokenStore: TokenStore,
    private val captchaGateway: CaptchaGateway,
    private val permissionCache: PermissionCache,
    private val securityProperties: SecurityProperties,
    private val tokenGenerator: TokenGenerator
) : CommandHandler<LoginCommand, LoginResult> {

    private val log = LoggerFactory.getLogger(LoginHandler::class.java)

    @Transactional
    override fun handle(command: LoginCommand): LoginResult {
        val user = userPort.findByUsernameAndActive(command.username)
            ?: throw InvalidCredentialsException()

        // Check account lock status
        when (val status = user.status) {
            is UserStatus.Locked -> {
                if (user.isLockExpired()) {
                    user.unlockIfExpired()
                } else {
                    throw AccountLockedException(status.until, status.reason)
                }
            }
            else -> { /* continue */ }
        }

        // CAPTCHA check (when failed login threshold exceeded)
        val maxAttempts = securityProperties.password.maxFailedAttempts
        if (user.failedLoginCount >= maxAttempts - 1) {
            val captchaToken = command.captchaToken
            if (captchaToken.isNullOrBlank()) {
                throw CaptchaRequiredException()
            }
            if (!captchaGateway.verify(captchaToken)) {
                throw CaptchaFailedException()
            }
        }

        // Validate password
        if (!tokenGenerator.matchesPassword(command.password, user.passwordHash.value)) {
            user.recordFailedLogin(maxAttempts, securityProperties.password.lockDurationMinutes * 60L)
            userPort.save(user)
            throw InvalidCredentialsException()
        }

        // Reset failed login count on success
        user.resetFailedLogins()
        userPort.save(user)

        // MFA checkpoint
        if (user.requiresMfa(command.trustedDeviceHash)) {
            return tokenGenerator.generateMfaResult(user.id.value, user.mfaMethod)
        }

        // Determine active domain
        val domainCode = command.domainCode ?: tokenGenerator.getPrimaryDomain(user.id.value)

        log.info("User logged in: {} domain: {}", user.username, domainCode)

        return LoginResult.Success(tokenGenerator.generateAuthResponse(user, domainCode))
    }

    override fun commandType(): Class<LoginCommand> = LoginCommand::class.java
}
