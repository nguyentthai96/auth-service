package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.LoginRateLimitService
import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.LoginSessionService
import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.SessionPolicyService
import com.ntt.authservice.auth.application.SessionPromotionService
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.event.IssuanceContext
import com.ntt.authservice.auth.domain.model.TokenIssuanceMetadata
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.rbac.application.query.GetUserRolesQuery
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
 *
 * Integration points:
 * - LoginRateLimitService: record failed/reset on success
 * - SessionPolicyService: enforce max sessions per role
 * - LoginSessionService: record login session + device info
 * - SessionPromotionService: promote anonymous session on login (best-effort)
 */
@Component
class LoginHandler(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val tokenStore: TokenStore,
    private val captchaGateway: CaptchaGateway,
    private val getUserRolesHandler: GetUserRolesHandler,
    private val securityProperties: SecurityProperties,
    private val tokenGenerator: TokenGenerator,
    private val passwordPolicyService: com.ntt.authservice.auth.application.PasswordPolicyService,
    private val loginRateLimitService: LoginRateLimitService,
    private val sessionPolicyService: SessionPolicyService,
    private val loginSessionService: LoginSessionService,
    private val sessionPromotionService: SessionPromotionService
) : CommandHandler<LoginCommand, LoginResult> {

    private val log = LoggerFactory.getLogger(LoginHandler::class.java)

    override fun commandType(): Class<LoginCommand> = LoginCommand::class.java

    @Transactional
    override fun handle(command: LoginCommand): LoginResult {
        val user = userPort.findByUsernameAndActive(command.username)
            ?: run {
                // Record failed attempt for rate limiting (even for non-existent users)
                loginRateLimitService.recordFailedAttempt(
                    ip = command.ipAddress ?: "unknown",
                    username = command.username,
                    deviceFingerprint = command.deviceFingerprint
                )
                throw InvalidCredentialsException()
            }

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

            // Record failed attempt for rate limiting
            loginRateLimitService.recordFailedAttempt(
                ip = command.ipAddress ?: "unknown",
                username = command.username,
                deviceFingerprint = command.deviceFingerprint
            )
            throw InvalidCredentialsException()
        }

        // Reset failed login count on success
        user.resetFailedLogins()
        userPort.save(user)

        // Reset rate limit counters on successful auth
        loginRateLimitService.resetOnSuccess(
            ip = command.ipAddress ?: "unknown",
            username = command.username,
            deviceFingerprint = command.deviceFingerprint
        )

        // Password expiry check
        val activeDomainCode = command.domainCode ?: tokenGenerator.getPrimaryDomain(user.id.value)
        val domainId = domainPort.findByCodeAndActive(activeDomainCode)?.id
        if (domainId != null && passwordPolicyService.isPasswordExpired(user.id.value, domainId)) {
            throw PasswordExpiredException()
        }

        // MFA checkpoint
        if (user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
            return tokenGenerator.generateMfaResult(user.id.value, user.mfaMethod)
        }

        // Determine active domain
        val domainCode = command.domainCode ?: tokenGenerator.getPrimaryDomain(user.id.value)

        // Load roles for session policy
        val domain = domainPort.findByCodeAndActive(domainCode)
        val roles = if (domain != null) {
            getUserRolesHandler.handle(GetUserRolesQuery(user.id.value, domain.id))
        } else {
            emptyList()
        }

        // Enforce session policy (may revoke oldest or throw)
        sessionPolicyService.enforcePolicy(user.id.value, roles)

        // Generate tokens
        val metadata = TokenIssuanceMetadata(
            issuanceContext = IssuanceContext.LOGIN,
            ipAddress = command.ipAddress,
            userAgent = command.userAgent
        )
        val authToken = tokenGenerator.generateAuthResponse(user, domainCode, metadata)

        // Record login session
        loginSessionService.recordLogin(
            userId = user.id.value,
            ipAddress = command.ipAddress ?: "unknown",
            userAgent = command.userAgent,
            deviceFingerprint = command.deviceFingerprint,
            refreshTokenId = null // Refresh token ID set separately if needed
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
                log.warn("Anonymous session promotion failed for session {}: {}", command.anonymousSessionId, e.message)
                PromotionResult(PromotionResult.Status.FAILED)
            }
        } else null

        log.info("User logged in: {} domain: {} ip: {}", user.username, domainCode, command.ipAddress)

        return LoginResult.Success(
            com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse.from(authToken),
            promotionResult
        )
    }
}
