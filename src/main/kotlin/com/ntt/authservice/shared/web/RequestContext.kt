package com.ntt.authservice.shared.web

import com.ntt.authservice.shared.exception.InvalidCredentialsException
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope
import java.util.Locale
import java.util.UUID

/**
 * Request-scoped bean that holds all request metadata in a centralized location.
 * Populated by [RequestContextFilter] after Spring Security filter chain.
 *
 * Provides a single source of truth for:
 * - Authentication data (userId, jti, roles)
 * - Client metadata (IP, user-agent, device fingerprint)
 * - Correlation/tracing (correlationId, requestId)
 * - Locale for i18n
 * - Anonymous session data
 * - Client app metadata (version, platform)
 *
 * FR-001: RequestContext bean.
 */
@Component
@RequestScope
class RequestContext {

    // === Authentication (from SecurityContext) ===
    var userId: Long? = null
    var username: String? = null
    var jti: String? = null
    var roles: Set<String> = emptySet()
    var authenticated: Boolean = false

    // === Request metadata (from HttpServletRequest headers) ===
    var clientIp: String = "unknown"
    var userAgent: String? = null
    var deviceFingerprint: String? = null

    // === Correlation & tracing ===
    var correlationId: String = UUID.randomUUID().toString()
    var requestId: String = UUID.randomUUID().toString()

    // === Locale ===
    var locale: Locale = Locale.getDefault()

    // === Anonymous session (optional) ===
    var anonymousSessionId: String? = null
    var anonymousTokenJti: String? = null

    // === Client metadata (absorbed from ClientMetadataFilter) ===
    var appVersion: String = "unknown"
    var clientPlatform: String = "unknown"

    /**
     * Returns the authenticated user's ID.
     * Throws [InvalidCredentialsException] if the user is not authenticated.
     */
    fun requireUserId(): Long =
        userId ?: throw InvalidCredentialsException()

    /**
     * Returns the JWT token ID (JTI).
     * Throws [InvalidCredentialsException] if not available.
     */
    fun requireJti(): String =
        jti ?: throw InvalidCredentialsException()

    /**
     * Checks if the authenticated user has the specified role.
     */
    fun hasRole(role: String): Boolean = roles.contains(role)

    /**
     * Checks if the authenticated user has admin privileges.
     */
    fun isAdmin(): Boolean =
        hasRole("ROLE_ADMIN") || hasRole("ROLE_SUPER_ADMIN")
}
