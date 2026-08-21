package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.AuthService
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

/**
 * Token management endpoints — introspection (RFC 7662), JWKS, session revocation.
 */
@RestController
class TokenController(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val messageSource: MessageSource
) {

    @PostMapping("/api/auth/introspect")
    fun introspect(@Valid @RequestBody request: IntrospectionRequest): ResponseEntity<IntrospectionResponse> {
        return try {
            val claims = jwtService.parseToken(request.token)
            val jti = claims.id
            val isBlacklisted = jti != null && tokenBlacklistRepository.existsByTokenJti(jti)

            ResponseEntity.ok(
                IntrospectionResponse(
                    active = !isBlacklisted,
                    sub = claims.subject,
                    username = claims["username"] as? String,
                    roles = claims["roles"] as? List<String>,
                    permissions = claims["permissions"] as? List<String>,
                    exp = claims.expiration?.time?.div(1000),
                    iat = claims.issuedAt?.time?.div(1000),
                    iss = claims.issuer,
                    jti = jti
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(IntrospectionResponse(active = false))
        }
    }

    @GetMapping("/.well-known/jwks.json")
    fun jwks(): ResponseEntity<Map<String, Any>> {
        val jwks = jwtService.getJwks()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
            .body(jwks)
    }

    @PostMapping("/api/auth/sessions/{userId}/revoke-all")
    fun revokeAllSessions(@PathVariable userId: Long): ResponseEntity<Map<String, Any?>> {
        val count = authService.revokeAllSessions(userId)
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.sessions_revoked_all", null, "All sessions revoked", locale)
        return ResponseEntity.ok(mapOf("revokedCount" to count, "userId" to userId, "message" to message))
    }
}
