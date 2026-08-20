package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.rbac.adapter.out.persistence.entity.RefreshTokenEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.exception.*
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.*

/**
 * Core authentication service — handles login, register, token refresh, domain switch.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val userDomainRepository: UserDomainRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val domainRepository: DomainRepository,
    private val rbacEngine: RbacEngine,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties,
    private val captchaVerifier: CaptchaVerifier,
    private val mfaService: MfaService,
    private val passwordPolicyService: PasswordPolicyService,
    private val auditLogService: AuditLogService
) {

    private val log = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        // Validate uniqueness
        if (userRepository.existsByUsername(request.username)) {
            throw DuplicateResourceException("User", "username", request.username)
        }
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException("User", "email", request.email)
        }

        // Validate domain exists
        val domain = domainRepository.findByCodeAndActiveTrue(request.domainCode)
            ?: throw ResourceNotFoundException("Domain", request.domainCode)

        // Create user
        val user = UserEntity().apply {
            username = request.username
            email = request.email
            passwordHash = passwordEncoder.encode(request.password)!!
            fullName = request.fullName
            phone = request.phone
            status = "ACTIVE"
        }
        val savedUser = userRepository.save(user)

        // Create domain membership
        val membership = com.ntt.authservice.rbac.adapter.out.persistence.entity.UserDomainEntity().apply {
            userId = savedUser.id!!
            domainId = domain.id!!
            isPrimary = true
        }
        userDomainRepository.save(membership)

        log.info("User registered: {} in domain: {}", savedUser.username, domain.code)

        return generateAuthResponse(savedUser, domain.code)
    }

    @Transactional
    fun login(request: LoginRequest): LoginResult {
        val user = userRepository.findByUsernameAndActiveTrue(request.username)
            ?: throw InvalidCredentialsException()

        // Check account locked
        if (user.status == "LOCKED") {
            val now = Instant.now()
            if (user.lockedUntilAt != null && user.lockedUntilAt!!.isAfter(now)) {
                throw AccountLockedException(user.lockedUntilAt!!)
            }
            // Lock expired — unlock
            user.status = "ACTIVE"
            user.failedLoginCount = 0
            user.lockedUntilAt = null
        }

        // CAPTCHA check (when failed login threshold exceeded)
        if (user.failedLoginCount >= securityProperties.password.maxFailedAttempts - 1) {
            val captchaToken = request.captchaToken
            if (captchaToken.isNullOrBlank()) {
                throw CaptchaRequiredException()
            }
            if (!captchaVerifier.verify(captchaToken)) {
                throw CaptchaFailedException()
            }
        }

        // Validate password
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            handleFailedLogin(user)
            throw InvalidCredentialsException()
        }

        // Reset failed login count on success
        user.failedLoginCount = 0
        userRepository.save(user)

        // Password expiry check — force change before issuing tokens
        val userDomains = userDomainRepository.findAllByUserIdAndActiveTrue(user.id!!)
        val primaryDomainMembership = userDomains.firstOrNull { it.isPrimary } ?: userDomains.firstOrNull()
        if (primaryDomainMembership != null) {
            if (passwordPolicyService.isPasswordExpired(user.id!!, primaryDomainMembership.domainId)) {
                throw PasswordExpiredException()
            }
        }

        // MFA checkpoint
        if (user.mfaEnabled && user.mfaMethod != "NONE") {
            // Check trusted device — skip MFA if matched
            val trustedHash = request.trustedDeviceHash
            if (trustedHash != null && trustedHash == user.trustedDeviceHash) {
                log.debug("Trusted device matched — skipping MFA for userId={}", user.id)
            } else {
                return mfaService.initiateMfa(user.id!!, user.mfaMethod)
            }
        }

        // Determine active domain
        val domainCode = request.domainCode ?: getPrimaryDomain(user.id!!)

        log.info("User logged in: {} domain: {}", user.username, domainCode)
        auditLogService.logEvent(user.id, AuditAction.LOGIN_SUCCESS, "User", user.id.toString(), "domain=$domainCode")

        return LoginResult.Success(generateAuthResponse(user, domainCode))
    }

    @Transactional
    fun refreshToken(refreshToken: String): AuthResponse {
        val tokenHash = hashToken(refreshToken)
        val storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
            ?: throw TokenExpiredException()

        if (storedToken.expiresAt.isBefore(Instant.now())) {
            storedToken.revoked = true
            refreshTokenRepository.save(storedToken)
            throw TokenExpiredException()
        }

        val user = userRepository.findById(storedToken.userId).orElseThrow {
            ResourceNotFoundException("User", storedToken.userId)
        }

        // Revoke old refresh token (rotation)
        storedToken.revoked = true
        refreshTokenRepository.save(storedToken)

        val domainCode = getPrimaryDomain(user.id!!)
        return generateAuthResponse(user, domainCode)
    }

    /**
     * Switch active domain without re-authentication (WF-06 fix from flow-logic-review).
     */
    @Transactional(readOnly = true)
    fun switchDomain(userId: Long, newDomainCode: String): AuthResponse {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        val domain = domainRepository.findByCodeAndActiveTrue(newDomainCode)
            ?: throw ResourceNotFoundException("Domain", newDomainCode)

        // Verify user is member of target domain
        userDomainRepository.findByUserIdAndDomainIdAndActiveTrue(userId, domain.id!!)
            ?: throw PermissionDeniedException("User is not a member of domain '$newDomainCode'")

        return generateAuthResponse(user, newDomainCode)
    }

    private fun generateAuthResponse(user: UserEntity, domainCode: String): AuthResponse {
        val domain = domainRepository.findByCodeAndActiveTrue(domainCode)
            ?: throw ResourceNotFoundException("Domain", domainCode)

        // Load user's domain memberships
        val userDomains = userDomainRepository.findAllByUserIdAndActiveTrue(user.id!!)
        val domainCodes = userDomains.mapNotNull { ud ->
            domainRepository.findById(ud.domainId).orElse(null)?.code
        }

        // Load roles and permissions for active domain
        val roles = rbacEngine.getUserRoles(user.id!!, domain.id!!)
        val permissions = rbacEngine.getEffectivePermissions(user.id!!, domain.id!!)
        val groups = emptyList<String>() // Simplified for now

        // Generate tokens
        val accessToken = jwtService.generateAccessToken(
            userId = user.id!!,
            username = user.username,
            domains = domainCodes,
            activeDomain = domainCode,
            roles = roles,
            permissions = permissions,
            groups = groups
        )

        val refreshToken = jwtService.generateRefreshToken(user.id!!)

        // Store refresh token hash
        val refreshEntity = RefreshTokenEntity().apply {
            userId = user.id!!
            tokenHash = hashToken(refreshToken)
            expiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs)
        }
        refreshTokenRepository.save(refreshEntity)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = "Bearer",
            expiresIn = securityProperties.jwt.accessTokenExpirationMs / 1000,
            userId = user.id!!,
            username = user.username,
            activeDomain = domainCode,
            roles = roles,
            permissions = permissions
        )
    }

    private fun handleFailedLogin(user: UserEntity) {
        user.failedLoginCount += 1
        if (user.failedLoginCount >= securityProperties.password.maxFailedAttempts) {
            user.status = "LOCKED"
            user.lockedUntilAt = Instant.now().plusSeconds(
                securityProperties.password.lockDurationMinutes * 60L
            )
            log.warn("User {} locked after {} failed attempts", user.username, user.failedLoginCount)
            auditLogService.logEvent(user.id, AuditAction.ACCOUNT_LOCKED, "User", user.id.toString(), "failedAttempts=${user.failedLoginCount}")
        } else {
            auditLogService.logEvent(user.id, AuditAction.LOGIN_FAILED, "User", user.id.toString(), "failedAttempts=${user.failedLoginCount}")
        }
        // updatedAt is auto-managed by AuditableEntity
        userRepository.save(user)
    }

    private fun getPrimaryDomain(userId: Long): String {
        val membership = userDomainRepository.findAllByUserIdAndActiveTrue(userId)
            .firstOrNull { it.isPrimary }
            ?: userDomainRepository.findAllByUserIdAndActiveTrue(userId).firstOrNull()
            ?: throw ResourceNotFoundException("DomainMembership", userId)

        return domainRepository.findById(membership.domainId).orElseThrow {
            ResourceNotFoundException("Domain", membership.domainId)
        }.code
    }

    /**
     * Build AuthResponse for a given userId (used by MFA verify and SSO callback).
     */
    fun buildAuthResponseForUser(userId: Long): AuthResponse {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }
        val domainCode = getPrimaryDomain(user.id!!)
        return generateAuthResponse(user, domainCode)
    }

    /**
     * Revoke all sessions for a user (force logout).
     * Revokes all active refresh tokens and ends all login sessions.
     * Access tokens expire naturally (max 15min) — industry standard for stateless JWT.
     */
    @Transactional
    fun revokeAllSessions(userId: Long): Int {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        // 1. Revoke all active refresh tokens
        val revokedCount = refreshTokenRepository.revokeAllByUserId(userId)

        // 2. Access tokens expire naturally (max 15min) — industry standard for JWT
        // No per-token JTI blacklisting for bulk revoke

        auditLogService.logEvent(userId, AuditAction.SESSION_REVOKED,
            entityType = "User", entityId = userId.toString(),
            details = "revokedTokens=$revokedCount")
        log.info("All sessions revoked for userId={}: {} tokens revoked", userId, revokedCount)
        return revokedCount
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray()))
    }
}

// DTOs
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
    val domainCode: String
)

data class LoginRequest(
    val username: String,
    val password: String,
    val domainCode: String? = null,
    val captchaToken: String? = null,
    val trustedDeviceHash: String? = null
)

