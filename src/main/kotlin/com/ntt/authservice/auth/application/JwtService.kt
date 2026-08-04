package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.TokenExpiredException
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT token generation and validation.
 * Uses HMAC-SHA256 for signing (configurable to RS256 in production).
 */
@Service
class JwtService(
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(JwtService::class.java)

    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(securityProperties.jwt.secretKey.toByteArray())
    }

    /**
     * Generate access token with embedded permissions.
     */
    fun generateAccessToken(
        userId: Long,
        username: String,
        domains: List<String>,
        activeDomain: String,
        roles: List<String>,
        permissions: List<String>,
        groups: List<String>
    ): String {
        val now = Date()
        val expiry = Date(now.time + securityProperties.jwt.accessTokenExpirationMs)

        return Jwts.builder()
            .subject(userId.toString())
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiry)
            .id(UUID.randomUUID().toString()) // JTI for token blacklist
            .claim("username", username)
            .claim("domains", domains)
            .claim("active_domain", activeDomain)
            .claim("roles", roles)
            .claim("permissions", permissions)
            .claim("groups", groups)
            .signWith(signingKey)
            .compact()
    }

    /**
     * Generate refresh token (minimal claims).
     */
    fun generateRefreshToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + securityProperties.jwt.refreshTokenExpirationMs)

        return Jwts.builder()
            .subject(userId.toString())
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiry)
            .id(UUID.randomUUID().toString())
            .claim("type", "refresh")
            .signWith(signingKey)
            .compact()
    }

    /**
     * Parse and validate token, returning claims.
     */
    fun parseToken(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw TokenExpiredException()
        } catch (e: JwtException) {
            log.warn("Invalid JWT token: {}", e.message)
            throw TokenExpiredException()
        }
    }

    fun getUserIdFromToken(token: String): Long {
        return parseToken(token).subject.toLong()
    }

    fun getJtiFromToken(token: String): String {
        return parseToken(token).id
    }

    fun isTokenValid(token: String): Boolean {
        return try {
            parseToken(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
