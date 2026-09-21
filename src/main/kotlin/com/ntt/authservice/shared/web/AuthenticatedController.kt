package com.ntt.authservice.shared.web

import org.springframework.security.access.AccessDeniedException

/**
 * Abstract controller for endpoints that require authentication.
 * Provides helper methods to access the authenticated user's identity.
 *
 * All methods delegate to [RequestContext] which is populated by [RequestContextFilter].
 *
 * FR-005: AuthenticatedController with currentUserId/jti/roles.
 */
abstract class AuthenticatedController : BaseController() {

    /**
     * Returns the authenticated user's ID.
     * Throws InvalidCredentialsException if user is not authenticated.
     * Behavioral equivalent of the 7 duplicate `getCurrentUserId()` private functions.
     */
    protected fun currentUserId(): Long = requestContext.requireUserId()

    /**
     * Returns the JWT token ID (JTI) of the current request.
     */
    protected fun currentJti(): String = requestContext.requireJti()

    /**
     * Returns the set of roles for the authenticated user.
     */
    protected fun currentRoles(): Set<String> = requestContext.roles

    /**
     * Verifies the authenticated user has the specified role.
     * Throws [AccessDeniedException] if the role is missing.
     */
    protected fun requireRole(role: String) {
        if (!requestContext.hasRole(role)) {
            throw AccessDeniedException("Required role: $role")
        }
    }
}
