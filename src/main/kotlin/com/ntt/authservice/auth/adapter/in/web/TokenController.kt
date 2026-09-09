package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.ClaimValidationStatus
import com.ntt.authservice.auth.application.ClaimValidatorChain
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.TokenBlacklistCacheService
import com.ntt.authservice.auth.application.command.RevokeSessionsCommand
import com.ntt.authservice.auth.application.command.RevokeSessionsHandler
import com.ntt.authservice.auth.domain.service.TokenHasher
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

/**
 * Token management endpoints — introspection (RFC 7662), JWKS, session revocation.
 *
 * FR-007: RFC 7662 compliant introspection with token_type, scope, client_id.
 * FR-008: JWKS endpoint with ETag support for conditional requests.
 * FR-011: Cache-based blacklist check (TokenBlacklistCacheService).
 */
@RestController
class TokenController(
    private val jwtService: JwtService,
    private val revokeSessionsHandler: RevokeSessionsHandler,
    private val tokenBlacklistCacheService: TokenBlacklistCacheService,
    private val claimValidatorChain: ClaimValidatorChain,
    private val messageSource: MessageSource
) {

    @PostMapping("/auth/introspect")
    fun introspect(@Valid @RequestBody request: IntrospectionRequest): ResponseEntity<IntrospectionResponse> {
        return try {
            val claims = jwtService.parseToken(request.token)
            val jti = claims.id

            // FR-011: Cache-based blacklist check
            val isBlacklisted = jti != null && tokenBlacklistCacheService.isBlacklisted(jti)

            // FR-007: Claim validation via chain (collect-all mode for diagnostic)
            val validationResults = claimValidatorChain.validateAll(claims)
            val hasClaimFailure = validationResults.any { it.status == ClaimValidationStatus.FAIL }

            val isActive = !isBlacklisted && !hasClaimFailure

            // FR-007: RFC 7662 fields — only populated when active=true
            val permissions = claims["permissions"] as? List<String>

            ResponseEntity.ok(
                IntrospectionResponse(
                    active = isActive,
                    sub = claims.subject,
                    username = claims["username"] as? String,
                    roles = claims["roles"] as? List<String>,
                    permissions = permissions,
                    exp = claims.expiration?.time?.div(1000),
                    iat = claims.issuedAt?.time?.div(1000),
                    iss = claims.issuer,
                    jti = jti,
                    tokenType = if (isActive) "Bearer" else null,
                    scope = if (isActive) permissions?.joinToString(" ") else null,
                    clientId = if (isActive) claims.audience?.firstOrNull() else null
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(IntrospectionResponse(active = false))
        }
    }

    /**
     * JWKS endpoint with ETag support (FR-008).
     * ETag is computed from kid(s) SHA-256 hash — changes on key rotation.
     */
    @GetMapping("/.well-known/jwks.json")
    fun jwks(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        val jwks = jwtService.getJwks()

        // FR-008: Compute ETag from kid(s)
        val keys = jwks["keys"] as? List<*> ?: emptyList<Any>()
        val kidString = keys
            .filterIsInstance<Map<*, *>>()
            .mapNotNull { it["kid"] as? String }
            .sorted()
            .joinToString(",")
        val etag = "\"${TokenHasher.hash(kidString)}\""

        // Conditional request support — If-None-Match → 304
        val ifNoneMatch = request.getHeader("If-None-Match")
        if (ifNoneMatch != null && ifNoneMatch == etag) {
            return ResponseEntity.status(304)
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .build()
        }

        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
            .body(jwks)
    }

    @DeleteMapping("/admin/sessions/users/{userId}")
    fun revokeAllSessions(@PathVariable userId: Long): ResponseEntity<Map<String, Any?>> {
        val count = revokeSessionsHandler.handle(RevokeSessionsCommand(userId))
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.sessions_revoked_all", null, "All sessions revoked", locale)
        return ResponseEntity.ok(mapOf("revokedCount" to count, "userId" to userId, "message" to message))
    }
}
