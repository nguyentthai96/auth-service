package com.ntt.authservice.auth.adapter.`in`.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that extracts client metadata headers and stores them in MDC for audit logging.
 *
 * Extracts:
 * - X-App-Version → MDC key "appVersion" (semver format, e.g., "1.2.3")
 * - X-Client-Platform → MDC key "clientPlatform" (web|ios|android|desktop)
 *
 * Runs early in the filter chain (HIGHEST_PRECEDENCE + 10) so all downstream
 * components have MDC context available for structured logging.
 *
 * MDC is cleaned up in finally block to prevent thread-pool leaks.
 *
 * FR-011: Client metadata filter for logging (X-App-Version, X-Client-Platform → MDC).
 * FR-008: X-App-Version header support.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ClientMetadataFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            MDC.put(MDC_APP_VERSION, request.getHeader(HEADER_APP_VERSION) ?: VALUE_UNKNOWN)
            MDC.put(MDC_CLIENT_PLATFORM, request.getHeader(HEADER_CLIENT_PLATFORM) ?: VALUE_UNKNOWN)
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_APP_VERSION)
            MDC.remove(MDC_CLIENT_PLATFORM)
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/")
    }

    companion object {
        private const val HEADER_APP_VERSION = "X-App-Version"
        private const val HEADER_CLIENT_PLATFORM = "X-Client-Platform"
        private const val MDC_APP_VERSION = "appVersion"
        private const val MDC_CLIENT_PLATFORM = "clientPlatform"
        private const val VALUE_UNKNOWN = "unknown"
    }
}
