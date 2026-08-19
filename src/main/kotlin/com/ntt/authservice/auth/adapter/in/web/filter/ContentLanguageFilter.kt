package com.ntt.authservice.auth.adapter.`in`.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that sets the Content-Language response header on all API responses.
 * Guarantees 100% coverage for NFR-004.
 *
 * Runs AFTER controller processing (response-phase) — sets Content-Language
 * from LocaleContextHolder which is populated by AcceptHeaderLocaleResolver.
 *
 * Defense-in-depth: AuthControllerAdvice also sets Content-Language for error responses.
 * Both set the same value — no conflict (last-write-wins with identical value).
 *
 * FR-006: Content-Language response header (100% coverage).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class ContentLanguageFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        filterChain.doFilter(request, response)
        // Set AFTER controller processing — LocaleContextHolder is populated
        response.setHeader(
            "Content-Language",
            LocaleContextHolder.getLocale().toLanguageTag()
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/")
    }
}
