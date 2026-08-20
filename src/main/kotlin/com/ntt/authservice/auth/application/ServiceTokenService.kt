package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

/**
 * Service token service — issues/validates JWTs for inter-service authentication (FR-021).
 * Service JWT is used for /api/internal/** endpoints between microservices.
 */
@Service
class ServiceTokenService(
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(ServiceTokenService::class.java)

    companion object {
        private const val SERVICE_TOKEN_TTL_SECONDS = 3600L // 1 hour
        private const val TOKEN_TYPE = "service"
    }

    /**
     * Generate a service JWT for inter-service communication.
     */
    fun generateServiceToken(serviceName: String): String {
        val now = Instant.now()
        val expiry = now.plusSeconds(SERVICE_TOKEN_TTL_SECONDS)

        return Jwts.builder()
            .subject(serviceName)
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("type", TOKEN_TYPE)
            .claim("service", serviceName)
            .signWith(getServiceSigningKey())
            .compact()
    }

    /**
     * Validate a service JWT and return the service name.
     * Throws exception if invalid or expired.
     */
    fun validateServiceToken(token: String): String {
        val claims = Jwts.parser()
            .verifyWith(getServiceSigningKey())
            .requireIssuer(securityProperties.jwt.issuer)
            .build()
            .parseSignedClaims(token)
            .payload

        val tokenType = claims["type"] as? String
        if (tokenType != TOKEN_TYPE) {
            throw IllegalArgumentException("Not a service token")
        }

        return claims.subject
    }

    private fun getServiceSigningKey(): SecretKey {
        // Use a separate service signing key (derived from main secret)
        val serviceSecret = securityProperties.jwt.secret + ":service"
        val keyBytes = serviceSecret.toByteArray().copyOf(32)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
