package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.TokenIssuanceMetadata
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.application.DomainLookupService
import com.ntt.authservice.auth.domain.event.IssuanceContext
import com.ntt.authservice.auth.domain.event.TokenIssuedEvent
import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.MfaService
import com.ntt.authservice.auth.application.event.TokenEventRecorder
import com.ntt.authservice.rbac.application.query.GetPermissionsHandler
import com.ntt.authservice.rbac.application.query.GetPermissionsQuery
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.rbac.application.query.GetUserRolesQuery
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Shared token generation logic — used by LoginHandler, RegisterHandler, RefreshTokenHandler.
 * Extracted from AuthService.generateAuthResponse().
 *
 * FR-008: Records TokenIssuedEvent via TokenEventRecorder after successful token generation.
 */
@Component
class TokenGenerator(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val tokenStore: TokenStore,
    private val jwtService: JwtService,
    private val mfaService: MfaService,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties,
    private val getPermissionsHandler: GetPermissionsHandler,
    private val getUserRolesHandler: GetUserRolesHandler,
    private val domainLookupService: DomainLookupService,
    private val tokenEventRecorder: TokenEventRecorder
) {

    fun generateAuthResponse(
        user: User,
        domainCode: String,
        metadata: TokenIssuanceMetadata? = null
    ): AuthToken {
        val domain = domainPort.findByCodeAndActive(domainCode)
            ?: throw ResourceNotFoundException("Domain", domainCode)

        // Load roles and permissions
        val roles = getUserRolesHandler.handle(GetUserRolesQuery(user.id.value, domain.id))
        val permissions = getPermissionsHandler.handle(GetPermissionsQuery(user.id.value, domain.id))

        // Pre-generate JTI for event correlation (DD-002)
        val accessTokenJti = UUID.randomUUID().toString()

        // Generate tokens
        val accessToken = jwtService.generateAccessToken(
            userId = user.id.value,
            username = user.username,
            domains = emptyList(), // populated by caller or lazy-loaded
            activeDomain = domainCode,
            roles = roles,
            permissions = permissions,
            groups = emptyList(),
            jti = accessTokenJti,
            deviceFingerprint = metadata?.deviceFingerprint
        )

        val refreshToken = jwtService.generateRefreshToken(user.id.value)

        // Store refresh token hash
        val tokenHash = TokenHasher.hash(refreshToken)
        tokenStore.saveRefreshToken(
            userId = user.id.value,
            tokenHash = tokenHash,
            expiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs)
        )

        // Record token issuance event (FR-008)
        val issuanceContext = metadata?.issuanceContext ?: IssuanceContext.LOGIN
        tokenEventRecorder.recordIssuance(
            event = TokenIssuedEvent(
                userId = user.id.value,
                username = user.username,
                domainCode = domainCode,
                domainId = domain.id,
                issuanceContext = issuanceContext,
                accessTokenJti = accessTokenJti,
                refreshTokenHash = tokenHash,
                roles = roles,
                permissions = permissions,
                accessTokenExpiresAt = Instant.now().plusMillis(securityProperties.jwt.accessTokenExpirationMs),
                refreshTokenExpiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs),
                previousRefreshTokenHash = metadata?.previousRefreshTokenHash,
                ipAddress = metadata?.ipAddress,
                userAgent = metadata?.userAgent
            ),
            userId = user.id.value,
            correlationId = metadata?.correlationId
        )

        return AuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = "Bearer",
            expiresIn = securityProperties.jwt.accessTokenExpirationMs / 1000,
            userId = user.id.value,
            username = user.username,
            activeDomain = domainCode,
            roles = roles,
            permissions = permissions
        )
    }

    fun getPrimaryDomain(userId: Long): String {
        return domainLookupService.getPrimaryDomainCode(userId)
    }

    fun generateMfaResult(userId: Long, mfaMethod: String): LoginResult {
        return mfaService.initiateMfa(userId, mfaMethod)
    }

    fun matchesPassword(rawPassword: String, encodedPassword: String): Boolean {
        return passwordEncoder.matches(rawPassword, encodedPassword)
    }

    fun encodePassword(rawPassword: String): String {
        return passwordEncoder.encode(rawPassword)!!
    }
}
