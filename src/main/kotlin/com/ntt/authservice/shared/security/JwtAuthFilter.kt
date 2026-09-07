package com.ntt.authservice.shared.security

import com.ntt.authservice.auth.application.ClaimValidationException
import com.ntt.authservice.auth.application.ClaimValidatorChain
import com.ntt.authservice.auth.application.FingerprintService
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.auth.application.TokenBlacklistCacheService
import com.ntt.authservice.auth.application.event.TokenEventRecorder
import com.ntt.authservice.auth.domain.event.TokenValidationFailedEvent
import com.ntt.authservice.auth.domain.event.ValidationFailureReason
import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.security.SignatureException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
 * Validates JWT, checks blacklist (cache-based), runs claim validation chain,
 * and sets SecurityContext with roles/permissions.
 *
 * FR-003: Cache-based blacklist check (TokenBlacklistCacheService).
 * FR-009: ClaimValidatorChain integration (fail-fast mode).
 * FR-011: Recognizes anonymous tokens (type=anonymous) and sets ROLE_ANONYMOUS authority.
 * FR-012: Validation failure event recording for suspicious patterns.
 * FR-014: Structured logging with reason categorization.
 * OBS-002: Micrometer metrics — validation counter + duration timer.
 */
@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val tokenBlacklistCacheService: TokenBlacklistCacheService,
    private val claimValidatorChain: ClaimValidatorChain,
    private val tokenEventRecorder: TokenEventRecorder,
    private val fingerprintService: FingerprintService,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry
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
        val sample = Timer.start(meterRegistry)

        try {
            val claims: Claims
            try {
                claims = jwtService.parseToken(token)
            } catch (e: io.jsonwebtoken.security.SignatureException) {
                // FR-012 + FR-014: Signature failure — suspicious, record event
                log.warn("JWT signature verification failed: ip={}", request.remoteAddr)
                recordValidationFailure(
                    reason = ValidationFailureReason.SIGNATURE_INVALID,
                    tokenJti = null,
                    validatorName = null,
                    request = request
                )
                meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "signature_invalid").increment()
                sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "failure").register(meterRegistry))
                filterChain.doFilter(request, response)
                return
            }

            // FR-003: Cache-based blacklist check
            val jti = claims.id
            if (jti != null && tokenBlacklistCacheService.isBlacklisted(jti)) {
                log.warn("Blacklisted token used: jti={}, ip={}", jti, request.remoteAddr)
                // FR-012: Record blacklisted token usage event
                recordValidationFailure(
                    reason = ValidationFailureReason.BLACKLISTED,
                    tokenJti = jti,
                    validatorName = null,
                    request = request
                )
                meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "blacklisted").increment()
                sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "failure").register(meterRegistry))
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                return
            }

            // FR-009: Claim validation chain (fail-fast)
            try {
                claimValidatorChain.validateOrThrow(claims)
            } catch (e: ClaimValidationException) {
                log.warn(
                    "Claim validation failed: validator={}, reason={}, jti={}, ip={}",
                    e.validatorName, e.message, jti, request.remoteAddr
                )
                // FR-012: Record claim validation failure event
                val reason = mapValidatorToReason(e.validatorName)
                recordValidationFailure(
                    reason = reason,
                    tokenJti = jti,
                    validatorName = e.validatorName,
                    request = request
                )
                val reasonTag = reason.name.lowercase()
                meterRegistry.counter("auth.token.validation", "result", "failure", "reason", reasonTag).increment()
                sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "failure").register(meterRegistry))
                // Continue without authentication — secured endpoints will reject
                filterChain.doFilter(request, response)
                return
            }

            // Step 4: Validate device fingerprint (FR-003)
            if (securityProperties.fingerprint.enabled && securityProperties.fingerprint.validationEnabled) {
                val claimFingerprint = claims["device_fingerprint"] as? String
                if (claimFingerprint != null) {
                    val requestFingerprint = fingerprintService.resolveFingerprint(request)
                    if (!fingerprintService.validateFingerprint(claimFingerprint, requestFingerprint)) {
                        if (securityProperties.fingerprint.strictMode) {
                            log.warn("Fingerprint mismatch (strict): jti={}, ip={}", jti, request.remoteAddr)
                            recordValidationFailure(
                                reason = ValidationFailureReason.FINGERPRINT_MISMATCH,
                                tokenJti = jti,
                                validatorName = "FingerprintValidator",
                                request = request
                            )
                            meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "fingerprint_mismatch").increment()
                            sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "failure").register(meterRegistry))
                            response.status = HttpServletResponse.SC_UNAUTHORIZED
                            return
                        } else {
                            log.warn("Fingerprint mismatch (lenient): jti={}, ip={}", jti, request.remoteAddr)
                            meterRegistry.counter("auth.fingerprint.mismatch", "mode", "lenient").increment()
                        }
                    }
                }
            }

            // Extract token type for anonymous vs authenticated branching (FR-011)
            val tokenType = claims["type"] as? String

            val authorities: List<SimpleGrantedAuthority>
            val authDetails: Map<String, Any>

            if (tokenType == "anonymous") {
                // Anonymous token — set ROLE_ANONYMOUS with limited privileges
                authorities = listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
                authDetails = mapOf(
                    "type" to "anonymous",
                    "sessionId" to (claims.subject ?: "")
                )
            } else {
                // Authenticated token — extract roles and permissions
                val userId = claims.subject
                val roles = (claims["roles"] as? List<*>)?.map { "ROLE_$it" } ?: emptyList()
                val permissions = (claims["permissions"] as? List<*>)?.map { it.toString() } ?: emptyList()

                authorities = roles.map { SimpleGrantedAuthority(it) } +
                        permissions.map { SimpleGrantedAuthority("PERM_$it") }
                authDetails = mapOf(
                    "activeDomain" to (claims["active_domain"] ?: ""),
                    "domains" to (claims["domains"] ?: emptyList<String>()),
                    "username" to (claims["username"] ?: "")
                )
            }

            val authentication = UsernamePasswordAuthenticationToken(
                claims.subject, null, authorities
            )
            authentication.details = authDetails

            SecurityContextHolder.getContext().authentication = authentication

            // OBS-002: Validation success
            meterRegistry.counter("auth.token.validation", "result", "success", "reason", "none").increment()
            sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "success").register(meterRegistry))

        } catch (e: Exception) {
            // FR-014: Structured logging — categorize failure
            log.debug("JWT validation failed: {}", e.message)
            meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "expired").increment()
            sample.stop(Timer.builder("auth.token.validation.duration").tag("result", "failure").register(meterRegistry))
            // Continue without authentication — secured endpoints will reject
        }

        filterChain.doFilter(request, response)
    }

    /**
     * Record a validation failure event via TokenEventRecorder (FR-012).
     * Fail-safe: recording failure does NOT affect the filter chain.
     */
    private fun recordValidationFailure(
        reason: ValidationFailureReason,
        tokenJti: String?,
        validatorName: String?,
        request: HttpServletRequest
    ) {
        try {
            val event = TokenValidationFailedEvent(
                reason = reason,
                tokenJti = tokenJti,
                ipAddress = request.remoteAddr,
                userAgent = request.getHeader("User-Agent"),
                validatorName = validatorName
            )
            tokenEventRecorder.recordValidationFailure(event, correlationId = null)
        } catch (e: Exception) {
            log.debug("Failed to record validation failure event: {}", e.message)
        }
    }

    /**
     * Map validator name to ValidationFailureReason for event recording.
     */
    private fun mapValidatorToReason(validatorName: String): ValidationFailureReason {
        return when (validatorName) {
            "IssuerClaimValidator" -> ValidationFailureReason.ISSUER_MISMATCH
            "AudienceClaimValidator" -> ValidationFailureReason.AUDIENCE_MISMATCH
            "TokenTypeClaimValidator" -> ValidationFailureReason.TYPE_REJECTED
            else -> ValidationFailureReason.CLAIM_VALIDATION_FAILED
        }
    }
}
