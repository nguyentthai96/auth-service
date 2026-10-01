package com.ntt.authservice.shared.web

import com.ntt.authservice.shared.exception.InvalidCredentialsException
import com.ntt.basecore.context.RequestContextHolder
import com.ntt.basecore.context.UserContext
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope
import java.util.Locale

/**
 * Service-level RequestContext that adapts to BaseCore's [RequestContextHolder].
 *
 * Provides a single source of truth for:
 * - Authentication data (userId, jti, roles)
 * - Client metadata (IP, user-agent, device fingerprint)
 * - Correlation/tracing (correlationId, requestId)
 * - Locale for i18n
 * - Anonymous session data
 * - Client app metadata (version, platform)
 *
 * FR-009: Compatibility Adapter for auth-service.
 */
@Component
@RequestScope
class RequestContext {

    // Fallback context for unit tests or unbound execution
    private val localContext = com.ntt.basecore.context.RequestContext()

    private val activeContext: com.ntt.basecore.context.RequestContext
        get() = RequestContextHolder.getOrNull() ?: localContext

    // === Authentication (from SecurityContext / UserContext) ===
    var userId: Long?
        get() = activeContext.userId
        set(value) {
            val user = activeContext.userContext ?: UserContext()
            activeContext.userContext = user.copy(userId = value, authenticated = value != null)
        }

    var username: String?
        get() = activeContext.username
        set(value) {
            val user = activeContext.userContext ?: UserContext()
            activeContext.userContext = user.copy(username = value)
        }

    var jti: String?
        get() = activeContext.jti
        set(value) {
            val user = activeContext.userContext ?: UserContext()
            activeContext.userContext = user.copy(jti = value)
        }

    var roles: Set<String>
        get() = activeContext.roles
        set(value) {
            val user = activeContext.userContext ?: UserContext()
            activeContext.userContext = user.copy(roles = value)
        }

    var authenticated: Boolean
        get() = activeContext.isAuthenticated
        set(value) {
            val user = activeContext.userContext ?: UserContext()
            activeContext.userContext = user.copy(authenticated = value)
        }

    // === Request metadata (from HttpServletRequest headers) ===
    var clientIp: String
        get() = activeContext.clientIp
        set(value) {
            activeContext.clientIp = value
        }

    var userAgent: String?
        get() = activeContext.userAgent
        set(value) {
            activeContext.userAgent = value
        }

    var deviceFingerprint: String?
        get() = activeContext.deviceFingerprint
        set(value) {
            activeContext.deviceFingerprint = value
        }

    // === Correlation & tracing ===
    var correlationId: String
        get() = activeContext.correlationId
        set(value) {
            activeContext.correlationId = value
        }

    var requestId: String
        get() = activeContext.requestId
        set(value) {
            activeContext.requestId = value
        }

    // === Locale ===
    var locale: Locale
        get() = org.springframework.context.i18n.LocaleContextHolder.getLocale()
        set(value) {
            activeContext.locale = value
            org.springframework.context.i18n.LocaleContextHolder.setLocale(value)
        }

    // === Anonymous session (optional) ===
    var anonymousSessionId: String?
        get() = activeContext.getAttribute("anonymousSessionId")
        set(value) {
            activeContext.setAttribute("anonymousSessionId", value)
        }

    var anonymousTokenJti: String?
        get() = activeContext.getAttribute("anonymousTokenJti")
        set(value) {
            activeContext.setAttribute("anonymousTokenJti", value)
        }

    // === Client metadata ===
    var appVersion: String
        get() = activeContext.appVersion
        set(value) {
            activeContext.appVersion = value
        }

    var clientPlatform: String
        get() = activeContext.clientPlatform
        set(value) {
            activeContext.clientPlatform = value
        }

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
