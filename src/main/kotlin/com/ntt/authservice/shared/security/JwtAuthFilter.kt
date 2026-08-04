package com.ntt.authservice.shared.security

import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
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
 * JWT Authentication Filter — Stage 1 PEP (Policy Enforcement Point).
 * Validates JWT, checks blacklist, and sets SecurityContext with roles/permissions.
 */
@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val tokenBlacklistRepository: TokenBlacklistRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)

        try {
            val claims = jwtService.parseToken(token)

            // Check token blacklist (SM-05 fix from flow-logic-review)
            val jti = claims.id
            if (jti != null && tokenBlacklistRepository.existsByTokenJti(jti)) {
                log.debug("Token {} is blacklisted", jti)
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                return
            }

            val userId = claims.subject
            val roles = (claims["roles"] as? List<*>)?.map { "ROLE_$it" } ?: emptyList()
            val permissions = (claims["permissions"] as? List<*>)?.map { it.toString() } ?: emptyList()

            val authorities = roles.map { SimpleGrantedAuthority(it) } +
                    permissions.map { SimpleGrantedAuthority("PERM_$it") }

            val authentication = UsernamePasswordAuthenticationToken(
                userId, null, authorities
            )

            // Store additional claims for downstream use
            authentication.details = mapOf(
                "activeDomain" to (claims["active_domain"] ?: ""),
                "domains" to (claims["domains"] ?: emptyList<String>()),
                "username" to (claims["username"] ?: "")
            )

            SecurityContextHolder.getContext().authentication = authentication

        } catch (e: Exception) {
            log.debug("JWT validation failed: {}", e.message)
            // Continue without authentication — secured endpoints will reject
        }

        filterChain.doFilter(request, response)
    }
}
