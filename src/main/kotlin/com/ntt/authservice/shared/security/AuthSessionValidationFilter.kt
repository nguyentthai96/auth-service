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
import com.ntt.basecore.autoconfigure.security.session.AbstractSessionValidationFilter
import com.ntt.basecore.autoconfigure.security.session.TokenValidationResult
import io.jsonwebtoken.Claims
import jakarta.servlet.http.HttpServletRequest
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Auth-service specific session validation filter — extends [AbstractSessionValidationFilter] pipeline.
 *
 * Adds domain-specific concerns on top of the base template-method pattern:
 * - Cache-based token blacklist check via [TokenBlacklistCacheService] (FR-007)
 * - Claim validation chain (ClaimValidatorChain) — fail-fast mode (FR-009)
 * - Device fingerprint validation via [FingerprintService] (FR-003)
 * - Anonymous token branching (type=anonymous → ROLE_ANONYMOUS) (FR-008)
 * - Validation failure event recording via [TokenEventRecorder] (FR-012)
 * - Micrometer metrics — validation counter + duration timer (OBS-002)
 *
 * Registered via [FilterRegistrationBean] at [OrderConstants.SESSION_VALIDATION] (-1700).
 * When this bean is present, [DefaultSessionValidationFilter] is auto-disabled
 * via @ConditionalOnMissingBean(AbstractSessionValidationFilter::class).
 *
 * @author nguyentthai96
 * @since 05/10/2026
 * @see AbstractSessionValidationFilter
 */
class AuthSessionValidationFilter(
    private val jwtService: JwtService,
    private val tokenBlacklistCacheService: TokenBlacklistCacheService,
    private val claimValidatorChain: ClaimValidatorChain,
    private val tokenEventRecorder: TokenEventRecorder,
    private val fingerprintService: FingerprintService,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry,
    publicPaths: List<String> = emptyList()
) : AbstractSessionValidationFilter(publicPaths) {

    private val log = LoggerFactory.getLogger(AuthSessionValidationFilter::class.java)

    override fun validateToken(token: String): TokenValidationResult {
        return try {
            val claims = jwtService.parseToken(token)

            // FR-009: Claim validation chain (fail-fast)
            try {
                claimValidatorChain.validateOrThrow(claims)
            } catch (e: ClaimValidationException) {
                log.warn(
                    "Claim validation failed: validator={}, reason={}, jti={}",
                    e.validatorName, e.message, claims.id
                )
                val reason = mapValidatorToReason(e.validatorName)
                recordValidationFailure(reason, claims.id, e.validatorName)
                meterRegistry.counter("auth.token.validation", "result", "failure", "reason", reason.name.lowercase()).increment()
                return TokenValidationResult.invalid("Claim validation failed: ${e.message}")
            }

            TokenValidationResult.valid(claims)
        } catch (e: io.jsonwebtoken.security.SignatureException) {
            log.warn("JWT signature verification failed")
            recordValidationFailure(ValidationFailureReason.SIGNATURE_INVALID, null, null)
            meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "signature_invalid").increment()
            TokenValidationResult.invalid("Signature verification failed")
        } catch (e: Exception) {
            log.debug("JWT validation failed: {}", e.message)
            meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "expired").increment()
            TokenValidationResult.invalid("Token validation error: ${e.message}")
        }
    }

    override fun isTokenRevoked(tokenId: String): Boolean {
        val revoked = tokenBlacklistCacheService.isBlacklisted(tokenId)
        if (revoked) {
            log.warn("Blacklisted token used: jti={}", tokenId)
            recordValidationFailure(ValidationFailureReason.BLACKLISTED, tokenId, null)
            meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "blacklisted").increment()
        }
        return revoked
    }

    override fun extractTokenId(principal: Any): String? {
        return (principal as? Claims)?.id
    }

    @Suppress("UNCHECKED_CAST")
    override fun onAuthenticationSuccess(request: HttpServletRequest, principal: Any) {
        val claims = principal as Claims

        // Validate device fingerprint (FR-003)
        if (securityProperties.fingerprint.enabled && securityProperties.fingerprint.validationEnabled) {
            val claimFingerprint = claims["device_fingerprint"] as? String
            if (claimFingerprint != null) {
                val requestFingerprint = fingerprintService.resolveFingerprint(request)
                if (!fingerprintService.validateFingerprint(claimFingerprint, requestFingerprint)) {
                    if (securityProperties.fingerprint.strictMode) {
                        log.warn("Fingerprint mismatch (strict): jti={}, ip={}", claims.id, request.remoteAddr)
                        recordValidationFailure(ValidationFailureReason.FINGERPRINT_MISMATCH, claims.id, "FingerprintValidator")
                        meterRegistry.counter("auth.token.validation", "result", "failure", "reason", "fingerprint_mismatch").increment()
                        // Strict mode: reject — AbstractSessionValidationFilter will return 401
                        throw SecurityException("Device fingerprint mismatch")
                    } else {
                        log.warn("Fingerprint mismatch (lenient): jti={}, ip={}", claims.id, request.remoteAddr)
                        meterRegistry.counter("auth.fingerprint.mismatch", "mode", "lenient").increment()
                    }
                }
            }
        }

        // Extract token type for anonymous vs authenticated branching (FR-008)
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
            val roles = (claims["roles"] as? List<*>)?.map { "ROLE_$it" } ?: emptyList()
            val permissions = (claims["permissions"] as? List<*>)?.map { it.toString() } ?: emptyList()

            authorities = roles.map { SimpleGrantedAuthority(it) } +
                    permissions.map { SimpleGrantedAuthority("PERM_$it") }
            authDetails = mapOf(
                "username" to (claims["username"] ?: "")
            )
        }

        val authentication = UsernamePasswordAuthenticationToken(
            claims.subject, null, authorities
        )
        authentication.details = authDetails
        SecurityContextHolder.getContext().authentication = authentication

        // OBS-002: Validation success metric
        meterRegistry.counter("auth.token.validation", "result", "success", "reason", "none").increment()
    }

    /**
     * Record a validation failure event via TokenEventRecorder (FR-012).
     * Fail-safe: recording failure does NOT affect the filter chain.
     */
    private fun recordValidationFailure(
        reason: ValidationFailureReason,
        tokenJti: String?,
        validatorName: String?
    ) {
        try {
            val event = TokenValidationFailedEvent(
                reason = reason,
                tokenJti = tokenJti,
                ipAddress = null,
                userAgent = null,
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
