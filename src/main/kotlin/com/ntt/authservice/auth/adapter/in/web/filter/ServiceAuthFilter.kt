package com.ntt.authservice.auth.adapter.`in`.web.filter

import com.ntt.authservice.auth.application.ServiceTokenService
import com.ntt.authservice.shared.exception.ServiceTokenInvalidException
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
 * Service authentication filter — validates service JWT for /api/internal/ endpoints (FR-021).
 * Extracts service name and scope from token and sets authentication context with ROLE_SERVICE.
 *
 * Error mapping:
 * - Missing token → 401 (AUTH_060)
 * - Invalid/expired token → 401 (AUTH_060)
 * - Unregistered service → 403 (AUTH_062)
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
            val claims = serviceTokenService.validateServiceToken(token)

            val authorities = listOf(
                SimpleGrantedAuthority("ROLE_SERVICE"),
                SimpleGrantedAuthority("ROLE_INTERNAL"),
                SimpleGrantedAuthority("SCOPE_${claims.scope}")
            )
            val authentication = UsernamePasswordAuthenticationToken(claims.serviceName, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication

            log.debug("Service authenticated: {} scope={}", claims.serviceName, claims.scope)
        } catch (e: ServiceTokenInvalidException) {
            log.warn("Service token validation failed (AUTH_060): {}", e.message)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.message)
            return
        } catch (e: Exception) {
            log.warn("Service token validation failed: {}", e.message)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid service token")
            return
        }

        filterChain.doFilter(request, response)
    }
}
