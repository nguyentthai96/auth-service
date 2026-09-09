package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.AnonymousSessionDataService
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.command.AnonymousSessionHandler
import com.ntt.authservice.auth.application.command.CreateAnonymousSessionCommand
import com.ntt.authservice.auth.application.command.RenewAnonymousTokenCommand
import com.ntt.authservice.auth.application.command.RenewAnonymousTokenHandler
import com.ntt.authservice.shared.exception.TokenExpiredException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for anonymous session management.
 * Base path: /api/v1/auth/anonymous
 *
 * Endpoints:
 * - POST /                 → Create anonymous session (public, rate-limited)
 * - POST /renew            → Renew anonymous token (requires anonymous bearer token)
 * - PUT /session/data      → Store session data (requires anonymous bearer token)
 * - GET /session/data      → Get session data (requires anonymous bearer token)
 * - DELETE /session/data   → Delete session data (requires anonymous bearer token)
 *
 * DD-005: Separate controller at /api/v1/auth/anonymous (not extending CqrsAuthController).
 */
@RestController
@RequestMapping("/auth/anonymous")
class AnonymousAuthController(
    private val anonymousSessionHandler: AnonymousSessionHandler,
    private val renewAnonymousTokenHandler: RenewAnonymousTokenHandler,
    private val anonymousSessionDataService: AnonymousSessionDataService,
    private val jwtService: JwtService
) {

    /**
     * Create an anonymous session — public endpoint (no auth required).
     * Rate-limited by IP address (FR-009).
     */
    @PostMapping("")
    fun createAnonymousSession(
        @RequestBody(required = false) request: CreateAnonymousSessionRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AnonymousTokenResponse> {
        val command = CreateAnonymousSessionCommand(
            ipAddress = extractClientIp(httpRequest),
            deviceFingerprint = request?.deviceFingerprint
                ?: httpRequest.getHeader("X-Device-Fingerprint")
        )

        val result = anonymousSessionHandler.handle(command)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            AnonymousTokenResponse(
                token = result.token,
                sessionId = result.sessionId,
                expiresIn = result.expiresIn
            )
        )
    }

    /**
     * Renew an anonymous token — requires valid anonymous bearer token (FR-008).
     * Blacklists the old JTI and issues a new token for the same session.
     */
    @PostMapping("/renew")
    fun renewAnonymousToken(
        httpRequest: HttpServletRequest
    ): ResponseEntity<AnonymousTokenResponse> {
        val token = extractBearerToken(httpRequest)

        val command = RenewAnonymousTokenCommand(currentToken = token)
        val result = renewAnonymousTokenHandler.handle(command)

        return ResponseEntity.ok(
            AnonymousTokenResponse(
                token = result.token,
                sessionId = result.sessionId,
                expiresIn = result.expiresIn
            )
        )
    }

    /**
     * Store data in an anonymous session namespace (FR-006).
     * Enforces data size limit per session (FR-007).
     */
    @PutMapping("/session/data")
    fun storeSessionData(
        @Valid @RequestBody request: StoreSessionDataRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        val sessionId = extractSessionIdFromToken(httpRequest)

        anonymousSessionDataService.storeData(
            sessionId = sessionId,
            namespace = request.namespace,
            key = request.key,
            value = request.value.toString()
        )

        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieve data from an anonymous session namespace (FR-006).
     */
    @GetMapping("/session/data")
    fun getSessionData(
        @RequestParam namespace: String,
        @RequestParam key: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<SessionDataResponse> {
        val sessionId = extractSessionIdFromToken(httpRequest)

        val value = anonymousSessionDataService.getData(sessionId, namespace, key)

        return ResponseEntity.ok(
            SessionDataResponse(
                namespace = namespace,
                key = key,
                value = value
            )
        )
    }

    /**
     * Delete data from an anonymous session namespace (DD-010).
     */
    @DeleteMapping("/session/data")
    fun deleteSessionData(
        @RequestParam namespace: String,
        @RequestParam key: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        val sessionId = extractSessionIdFromToken(httpRequest)

        anonymousSessionDataService.deleteData(sessionId, namespace, key)

        return ResponseEntity.noContent().build()
    }

    /**
     * Extract session ID from the anonymous bearer token in the Authorization header.
     */
    private fun extractSessionIdFromToken(request: HttpServletRequest): String {
        val token = extractBearerToken(request)
        val claims = jwtService.parseAnonymousToken(token)
        return claims.subject
    }

    /**
     * Extract bearer token from the Authorization header.
     */
    private fun extractBearerToken(request: HttpServletRequest): String {
        val authHeader = request.getHeader("Authorization")
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            throw TokenExpiredException()
        }
        return authHeader.substring(7)
    }

    /**
     * Extract client IP address, respecting X-Forwarded-For header.
     */
    private fun extractClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }
}
