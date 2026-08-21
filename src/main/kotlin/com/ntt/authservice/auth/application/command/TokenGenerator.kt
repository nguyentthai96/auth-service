package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.application.DomainLookupService
import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.MfaService
import com.ntt.authservice.rbac.application.query.GetPermissionsHandler
import com.ntt.authservice.rbac.application.query.GetPermissionsQuery
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.rbac.application.query.GetUserRolesQuery
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Shared token generation logic — used by LoginHandler, RegisterHandler, RefreshTokenHandler.
 * Extracted from AuthService.generateAuthResponse().
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
    private val domainLookupService: DomainLookupService
) {

    fun generateAuthResponse(user: User, domainCode: String): AuthToken {
        val domain = domainPort.findByCodeAndActive(domainCode)
            ?: throw ResourceNotFoundException("Domain", domainCode)

        // Load roles and permissions
        val roles = getUserRolesHandler.handle(GetUserRolesQuery(user.id.value, domain.id))
        val permissions = getPermissionsHandler.handle(GetPermissionsQuery(user.id.value, domain.id))

        // Generate tokens
        val accessToken = jwtService.generateAccessToken(
            userId = user.id.value,
            username = user.username,
            domains = emptyList(), // populated by caller or lazy-loaded
            activeDomain = domainCode,
            roles = roles,
            permissions = permissions,
            groups = emptyList()
        )

        val refreshToken = jwtService.generateRefreshToken(user.id.value)

        // Store refresh token hash
        val tokenHash = TokenHasher.hash(refreshToken)
        tokenStore.saveRefreshToken(
            userId = user.id.value,
            tokenHash = tokenHash,
            expiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs)
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
