package com.ntt.authservice.shared.web

import com.ntt.basecore.context.RequestContextHolder
import com.ntt.basecore.context.UserContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that enriches [RequestContext] from Spring SecurityContext.
 *
 * Runs AFTER Spring Security filter chain (order = LOWEST_PRECEDENCE - 20)
 * so that SecurityContext is already populated with authentication data.
 *
 * Absorbs MDC logic previously in ClientMetadataFilter (X-App-Version, X-Client-Platform).
 *
 * MDC keys set: correlationId, userId, clientIp, appVersion, clientPlatform.
 * All MDC keys are cleaned up in finally block to prevent thread-pool leaks.
 *
 * FR-002: Populate RequestContext.
 * FR-003: MDC propagation.
 * FR-008: Correlation ID extraction.
 * FR-009: Client IP extraction.
 */
@Component("appRequestContextFilter")
@Order(Ordered.LOWEST_PRECEDENCE - 20)
class RequestContextFilter(
    private val requestContextProvider: ObjectProvider<RequestContext>
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ctx = requestContextProvider.ifAvailable

        try {
            if (ctx != null) {
                populateFromSecurityContext(ctx)
            }
            filterChain.doFilter(request, response)
        } finally {
            // userId MDC cleanup if set
            MDC.remove(MDC_USER_ID)
        }
    }

    /**
     * Extract authentication data from Spring SecurityContext and synchronize with RequestContext.
     */
    private fun populateFromSecurityContext(ctx: RequestContext) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.isAuthenticated &&
            authentication.principal != "anonymousUser"
        ) {
            val userId = (authentication.principal as? String)?.toLongOrNull()
            val username = authentication.name
            val roles = authentication.authorities
                ?.mapNotNull { it.authority }
                ?.toSet()
                ?: emptySet()
            val jti = authentication.credentials as? String

            ctx.authenticated = true
            ctx.userId = userId
            ctx.username = username
            ctx.roles = roles
            ctx.jti = jti

            // Synchronize with BaseCore RequestContextHolder UserContext
            val userContext = UserContext(
                userId = userId,
                username = username,
                roles = roles,
                jti = jti,
                authenticated = true
            )
            RequestContextHolder.setUserContext(userContext)

            userId?.let { MDC.put(MDC_USER_ID, it.toString()) }
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return !uri.startsWith("/auth") && !uri.startsWith("/api/")
    }

    companion object {
        private const val MDC_USER_ID = "userId"
    }
}
