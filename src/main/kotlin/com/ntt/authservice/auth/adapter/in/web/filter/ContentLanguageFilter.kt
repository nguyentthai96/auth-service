package com.ntt.authservice.auth.adapter.`in`.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.LocaleResolver

/**
 * Servlet filter that sets the Content-Language response header on all API responses.
 * Guarantees 100% coverage for NFR-004.
 *
 * Runs BEFORE controller processing — resolves locale via LocaleResolver (applying [en, vi] whitelist)
 * and sets Content-Language response header + LocaleContextHolder.
 *
 * FR-006: Content-Language response header (100% coverage).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class ContentLanguageFilter(
    private val localeResolver: LocaleResolver
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val resolvedLocale = localeResolver.resolveLocale(request)
        LocaleContextHolder.setLocale(resolvedLocale)
        try {
            response.setHeader("Content-Language", resolvedLocale.toLanguageTag())
            filterChain.doFilter(request, response)
        } finally {
            LocaleContextHolder.resetLocaleContext()
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return !uri.startsWith("/auth") && !uri.startsWith("/api/")
    }
}
