package com.ntt.authservice.auth.adapter.`in`.web.filter

import com.ntt.authservice.auth.application.ServiceTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Service authentication filter — validates service JWT for /api/internal/** paths (FR-021).
 * Extracts service name from token and sets authentication context with ROLE_SERVICE.
 */
@Component
class ServiceAuthFilter(
    private val serviceTokenService: ServiceTokenService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ServiceAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Only process /api/internal/** paths
        if (!request.requestURI.startsWith("/api/internal/")) {
            filterChain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing service token")
            return
        }

        val token = authHeader.removePrefix("Bearer ").trim()

        try {
            val serviceName = serviceTokenService.validateServiceToken(token)

            val authorities = listOf(
                SimpleGrantedAuthority("ROLE_SERVICE"),
                SimpleGrantedAuthority("ROLE_INTERNAL")
            )
            val authentication = UsernamePasswordAuthenticationToken(serviceName, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication

            log.debug("Service authenticated: {}", serviceName)
        } catch (e: Exception) {
            log.warn("Service token validation failed: {}", e.message)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid service token")
            return
        }

        filterChain.doFilter(request, response)
    }
}
