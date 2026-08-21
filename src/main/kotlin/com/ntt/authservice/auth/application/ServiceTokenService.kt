package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.ServiceTokenInvalidException
import com.ntt.authservice.shared.exception.ServiceInsufficientScopeException
import com.ntt.authservice.shared.exception.ServiceNotRegisteredException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

/**
 * Service token service — issues/validates JWTs for inter-service authentication (FR-021).
 * Service JWT is used for /api/internal/ endpoints between microservices.
 *
 * Error codes:
 * - AUTH_060: Invalid service token (signature/format/expired)
 * - AUTH_061: Insufficient scope for the requested operation
 * - AUTH_062: Service not registered / unknown service name
 */
@Service
class ServiceTokenService(
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(ServiceTokenService::class.java)

    companion object {
        private const val SERVICE_TOKEN_TTL_SECONDS = 3600L // 1 hour
        private const val TOKEN_TYPE = "service"
        /** Registered service names allowed to obtain service tokens. */
        private val REGISTERED_SERVICES = setOf(
            "auth-service", "account-service", "system-admin-service"
        )
    }

    /**
     * Generate a service JWT for inter-service communication.
     * @throws ServiceNotRegisteredException if serviceName is not in the registered list
     */
    fun generateServiceToken(serviceName: String, scope: String = "INTERNAL"): String {
        if (serviceName !in REGISTERED_SERVICES) {
            throw ServiceNotRegisteredException(serviceName)
        }

        val now = Instant.now()
        val expiry = now.plusSeconds(SERVICE_TOKEN_TTL_SECONDS)

        return Jwts.builder()
            .subject(serviceName)
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("type", TOKEN_TYPE)
            .claim("service", serviceName)
            .claim("scope", scope)
            .signWith(getServiceSigningKey())
            .compact()
    }

    /**
     * Validate a service JWT and return the service claims.
     * @throws ServiceTokenInvalidException if token is invalid, expired, or malformed (AUTH_060)
     */
    fun validateServiceToken(token: String): ServiceClaims {
        try {
            val claims = Jwts.parser()
                .verifyWith(getServiceSigningKey())
                .requireIssuer(securityProperties.jwt.issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            val tokenType = claims["type"] as? String
            if (tokenType != TOKEN_TYPE) {
                throw ServiceTokenInvalidException("Not a service token")
            }

            val serviceName = claims.subject
            val scope = claims["scope"] as? String ?: "INTERNAL"

            return ServiceClaims(serviceName = serviceName, scope = scope)
        } catch (e: ServiceTokenInvalidException) {
            throw e
        } catch (e: Exception) {
            throw ServiceTokenInvalidException("Service token validation failed: ${e.message}")
        }
    }

    /**
     * Check if a service is authorized for a specific scope.
     * @throws ServiceInsufficientScopeException if scope does not match (AUTH_061)
     */
    fun isServiceAuthorized(claims: ServiceClaims, requiredScope: String): Boolean {
        if (claims.scope != requiredScope && claims.scope != "INTERNAL") {
            throw ServiceInsufficientScopeException(claims.serviceName, requiredScope)
        }
        return true
    }

    private fun getServiceSigningKey(): SecretKey {
        // Use a separate service signing key (derived from main secret)
        val serviceSecret = securityProperties.jwt.secretKey + ":service"
        val keyBytes = serviceSecret.toByteArray().copyOf(32)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}

/**
 * Claims extracted from a validated service JWT.
 */
data class ServiceClaims(
    val serviceName: String,
    val scope: String = "INTERNAL"
)
