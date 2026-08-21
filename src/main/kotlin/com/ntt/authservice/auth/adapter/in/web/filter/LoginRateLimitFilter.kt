package com.ntt.authservice.auth.adapter.`in`.web.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.application.LoginRateLimitService
import com.ntt.authservice.shared.exception.RateLimitExceededException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI
import java.util.Locale

/**
 * Pre-authentication rate limit filter for login endpoint.
 * Intercepts POST /api/auth/login and checks multi-dimensional rate limits
 * BEFORE the request reaches LoginHandler.
 *
 * Uses LoginRateLimitService (Redis INCR pattern).
 *
 * Note: This filter runs in the Spring Security filter chain BEFORE DispatcherServlet,
 * so LocaleContextHolder is not populated yet. We parse Accept-Language directly
 * using Locale.LanguageRange for i18n ProblemDetail responses.
 */
@Component
class LoginRateLimitFilter(
    private val loginRateLimitService: LoginRateLimitService,
    private val objectMapper: ObjectMapper,
    private val messageSource: MessageSource
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(LoginRateLimitFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // Only intercept POST /api/auth/login
        return !(request.method == "POST" && request.requestURI == "/api/auth/login")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ip = extractClientIp(request)
        val deviceFingerprint = request.getHeader("X-Device-Fingerprint")

        // Extract username from request body — use cached wrapper for re-reading
        val username = try {
            extractUsernameFromBody(request)
        } catch (ex: Exception) {
            log.debug("Could not extract username from request body for rate limiting")
            null
        }

        try {
            // Check existing locks (pre-auth — before consuming the request body)
            loginRateLimitService.checkMultiDimensional(
                ip = ip,
                username = username ?: "",
                deviceFingerprint = deviceFingerprint
            )
        } catch (ex: RateLimitExceededException) {
            writeRateLimitResponse(request, response, ex)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }

    /**
     * Extract username from the request body by peeking at the JSON.
     * Note: This requires a cached request wrapper if the body needs to be read again.
     * For simplicity, we extract from query params or headers first;
     * if not available, we skip username-level rate limiting at filter level
     * (it will still be enforced at handler level via recordFailedAttempt).
     */
    private fun extractUsernameFromBody(request: HttpServletRequest): String? {
        // Try to get from a custom header first (lighter weight)
        val headerUsername = request.getHeader("X-Login-Username")
        if (!headerUsername.isNullOrBlank()) return headerUsername

        // Cannot safely read body in filter without wrapping — skip username check at filter level.
        // Username-based rate limiting is enforced at LoginHandler via recordFailedAttempt.
        return null
    }

    /**
     * Resolve locale from Accept-Language header.
     *
     * This filter runs BEFORE DispatcherServlet, so AcceptHeaderLocaleResolver
     * (configured in I18nConfig) hasn't populated LocaleContextHolder yet.
     * We parse the header directly using JDK Locale.LanguageRange.
     *
     * Supported locales mirror I18nConfig: [en, vi]. Default: en.
     */
    private fun resolveLocale(request: HttpServletRequest): Locale {
        val acceptLanguage = request.getHeader("Accept-Language") ?: return Locale.ENGLISH
        return try {
            val ranges = Locale.LanguageRange.parse(acceptLanguage)
            val supported = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi"))
            Locale.lookup(ranges, supported) ?: Locale.ENGLISH
        } catch (e: Exception) {
            Locale.ENGLISH
        }
    }

    private fun writeRateLimitResponse(
        request: HttpServletRequest,
        response: HttpServletResponse,
        ex: RateLimitExceededException
    ) {
        val locale = resolveLocale(request)
        val detail = messageSource.getMessage(
            "auth.rate_limited",
            arrayOf<Any>(ex.retryAfterSeconds, ex.dimension),
            ex.message,
            locale
        )

        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail)
        problem.title = "AUTH_020"
        problem.type = URI.create("https://auth-service/errors/rate_limited")
        problem.setProperty("errorCode", "AUTH_020")
        problem.setProperty("retryAfterSeconds", ex.retryAfterSeconds)
        problem.setProperty("dimension", ex.dimension)

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader("Retry-After", ex.retryAfterSeconds.toString())
        response.setHeader("Content-Language", locale.toLanguageTag())
        response.writer.write(objectMapper.writeValueAsString(problem))
    }
}
