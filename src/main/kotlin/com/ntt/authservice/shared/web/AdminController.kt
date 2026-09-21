package com.ntt.authservice.shared.web

import org.springframework.security.access.AccessDeniedException

/**
 * Abstract controller for admin-only endpoints.
 * Adds admin role verification on top of [AuthenticatedController].
 *
 * FR-006: AdminController with requireAdmin().
 */
abstract class AdminController : AuthenticatedController() {

    /**
     * Verifies the authenticated user has admin privileges (ROLE_ADMIN or ROLE_SUPER_ADMIN).
     * Throws [AccessDeniedException] if the user is not an admin.
     */
    protected fun requireAdmin() {
        if (!requestContext.isAdmin()) {
            throw AccessDeniedException("Admin privileges required")
        }
    }
}
