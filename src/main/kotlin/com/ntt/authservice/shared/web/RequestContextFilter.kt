package com.ntt.authservice.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Servlet filter that populates [RequestContext] from SecurityContext and HTTP headers,
 * and sets MDC keys for structured logging.
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
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
class RequestContextFilter(
    private val requestContextProvider: ObjectProvider<RequestContext>
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ctx = requestContextProvider.ifAvailable ?: run {
            filterChain.doFilter(request, response)
            return
        }

        try {
            populateFromSecurityContext(ctx)
            populateFromHeaders(ctx, request)
            populateClientMetadata(ctx, request)
            populateLocale(ctx)
            setMdc(ctx)

            filterChain.doFilter(request, response)
        } finally {
            clearMdc()
        }
    }

    /**
     * Extract authentication data from Spring SecurityContext.
     */
    private fun populateFromSecurityContext(ctx: RequestContext) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.isAuthenticated &&
            authentication.principal != "anonymousUser"
        ) {
            ctx.authenticated = true
            ctx.userId = (authentication.principal as? String)?.toLongOrNull()
            ctx.username = authentication.name
            ctx.roles = authentication.authorities
                ?.mapNotNull { it.authority }
                ?.toSet()
                ?: emptySet()

            // Extract JTI from credentials if available
            ctx.jti = authentication.credentials as? String
        }
    }

    /**
     * Extract request metadata from HTTP headers.
     * FR-008: Correlation ID priority: X-Correlation-ID → X-Request-ID → auto-generated UUID.
     * FR-009: Client IP priority: X-Forwarded-For (first value) → remoteAddr.
     */
    private fun populateFromHeaders(ctx: RequestContext, request: HttpServletRequest) {
        // Correlation ID (FR-008)
        ctx.correlationId = request.getHeader(HEADER_CORRELATION_ID)
            ?: request.getHeader(HEADER_REQUEST_ID)
            ?: UUID.randomUUID().toString()

        // Request ID (always unique per request)
        ctx.requestId = UUID.randomUUID().toString()

        // Client IP (FR-009)
        val forwarded = request.getHeader(HEADER_X_FORWARDED_FOR)
        ctx.clientIp = if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }

        // User-Agent
        ctx.userAgent = request.getHeader(HEADER_USER_AGENT)

        // Device fingerprint
        ctx.deviceFingerprint = request.getHeader(HEADER_DEVICE_FINGERPRINT)
    }

    /**
     * Extract client application metadata (absorbed from ClientMetadataFilter).
     */
    private fun populateClientMetadata(ctx: RequestContext, request: HttpServletRequest) {
        ctx.appVersion = request.getHeader(HEADER_APP_VERSION) ?: VALUE_UNKNOWN
        ctx.clientPlatform = request.getHeader(HEADER_CLIENT_PLATFORM) ?: VALUE_UNKNOWN
    }

    /**
     * Set locale from Spring's LocaleContextHolder (populated by AcceptHeaderLocaleResolver).
     */
    private fun populateLocale(ctx: RequestContext) {
        ctx.locale = LocaleContextHolder.getLocale()
    }

    /**
     * Propagate request context data to MDC for structured logging.
     */
    private fun setMdc(ctx: RequestContext) {
        MDC.put(MDC_CORRELATION_ID, ctx.correlationId)
        MDC.put(MDC_CLIENT_IP, ctx.clientIp)
        MDC.put(MDC_APP_VERSION, ctx.appVersion)
        MDC.put(MDC_CLIENT_PLATFORM, ctx.clientPlatform)
        ctx.userId?.let { MDC.put(MDC_USER_ID, it.toString()) }
    }

    /**
     * Remove all MDC keys to prevent thread-pool leaks.
     */
    private fun clearMdc() {
        MDC.remove(MDC_CORRELATION_ID)
        MDC.remove(MDC_USER_ID)
        MDC.remove(MDC_CLIENT_IP)
        MDC.remove(MDC_APP_VERSION)
        MDC.remove(MDC_CLIENT_PLATFORM)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/")
    }

    companion object {
        // Header names
        private const val HEADER_CORRELATION_ID = "X-Correlation-ID"
        private const val HEADER_REQUEST_ID = "X-Request-ID"
        private const val HEADER_X_FORWARDED_FOR = "X-Forwarded-For"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_DEVICE_FINGERPRINT = "X-Device-Fingerprint"
        private const val HEADER_APP_VERSION = "X-App-Version"
        private const val HEADER_CLIENT_PLATFORM = "X-Client-Platform"

        // MDC keys
        private const val MDC_CORRELATION_ID = "correlationId"
        private const val MDC_USER_ID = "userId"
        private const val MDC_CLIENT_IP = "clientIp"
        private const val MDC_APP_VERSION = "appVersion"
        private const val MDC_CLIENT_PLATFORM = "clientPlatform"

        // Defaults
        private const val VALUE_UNKNOWN = "unknown"
    }
}
