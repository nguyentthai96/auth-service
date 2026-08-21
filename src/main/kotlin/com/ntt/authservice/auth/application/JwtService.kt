package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.TokenExpiredException
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT token generation and validation.
 * Supports RS256 (primary) with HMAC-SHA256 fallback for 7-day migration.
 */
@Service
class JwtService(
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(JwtService::class.java)

    // RS256 key pair (primary signing key)
    private val keyPair: KeyPair? by lazy {
        val props = securityProperties.jwt
        if (props.privateKeyPath.isNotBlank() && props.publicKeyPath.isNotBlank()) {
            loadKeyPair(props.privateKeyPath, props.publicKeyPath)
        } else {
            log.warn("RS256 key pair not configured — falling back to HMAC-SHA256")
            null
        }
    }

    // HMAC legacy key (7-day migration fallback)
    private val legacyKey: SecretKey? by lazy {
        val secretKey = securityProperties.jwt.secretKey
        if (secretKey.isNotBlank()) {
            Keys.hmacShaKeyFor(secretKey.toByteArray())
        } else {
            null
        }
    }

    private fun loadKeyPair(privateKeyPath: String, publicKeyPath: String): KeyPair {
        val kf = KeyFactory.getInstance("RSA")

        val privateKeyPem = Files.readString(Path.of(privateKeyPath))
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyPem)))

        val publicKeyPem = Files.readString(Path.of(publicKeyPath))
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val publicKey = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPem)))

        log.info("RS256 key pair loaded successfully (kid={})", securityProperties.jwt.keyId)
        return KeyPair(publicKey, privateKey)
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
        groups: List<String>,
        jti: String? = null  // Pre-generated JTI for event correlation (FR-007)
    ): String {
        val now = Date()
        val expiry = Date(now.time + securityProperties.jwt.accessTokenExpirationMs)
        val resolvedJti = jti ?: UUID.randomUUID().toString()

        val builder = Jwts.builder()
            .subject(userId.toString())
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiry)
            .id(resolvedJti)
            .claim("username", username)
            .claim("domains", domains)
            .claim("active_domain", activeDomain)
            .claim("roles", roles)
            .claim("permissions", permissions)
            .claim("groups", groups)

        return signToken(builder)
    }

    /**
     * Generate refresh token (minimal claims).
     */
    fun generateRefreshToken(userId: Long, jti: String? = null): String {
        val now = Date()
        val expiry = Date(now.time + securityProperties.jwt.refreshTokenExpirationMs)
        val resolvedJti = jti ?: UUID.randomUUID().toString()

        val builder = Jwts.builder()
            .subject(userId.toString())
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiry)
            .id(resolvedJti)
            .claim("type", "refresh")

        return signToken(builder)
    }

    /**
     * Generate MFA challenge token (short-lived, 5 min).
     */
    fun generateMfaToken(userId: Long, method: String): String {
        val now = Date()
        val expiry = Date(now.time + securityProperties.mfa.mfaTokenTtlSeconds * 1000)

        val builder = Jwts.builder()
            .subject(userId.toString())
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiry)
            .id(UUID.randomUUID().toString())
            .claim("type", "mfa")
            .claim("method", method)

        return signToken(builder)
    }

    /**
     * Parse MFA token and validate type claim.
     */
    fun parseMfaToken(token: String): Claims {
        val claims = parseToken(token)
        val type = claims["type"] as? String
        if (type != "mfa") {
            throw TokenExpiredException()
        }
        return claims
    }

    /**
     * Generate anonymous session token with type=anonymous claim.
     * Follows generateMfaToken() pattern — custom claims, configurable TTL.
     */
    fun generateAnonymousToken(sessionId: String): String {
        val now = Date()
        val expiry = Date(now.time + securityProperties.anonymous.tokenTtlSeconds * 1000)

        val builder = Jwts.builder()
            .subject(sessionId)
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiry)
            .id(UUID.randomUUID().toString())
            .claim("type", "anonymous")

        return signToken(builder)
    }

    /**
     * Parse anonymous token and validate type=anonymous claim.
     * Follows parseMfaToken() pattern.
     */
    fun parseAnonymousToken(token: String): Claims {
        val claims = parseToken(token)
        val type = claims["type"] as? String
        if (type != "anonymous") {
            throw TokenExpiredException()
        }
        return claims
    }

    private fun signToken(builder: JwtBuilder): String {
        val kp = keyPair
        return if (kp != null) {
            builder
                .header().keyId(securityProperties.jwt.keyId).and()
                .signWith(kp.private, Jwts.SIG.RS256)
                .compact()
        } else {
            val lk = legacyKey ?: throw IllegalStateException("No signing key configured")
            builder.signWith(lk).compact()
        }
    }

    /**
     * Parse and validate token, returning claims.
     * Tries RS256 first, falls back to HMAC for migration period.
     */
    fun parseToken(token: String): Claims {
        // Try RS256 first
        val kp = keyPair
        if (kp != null) {
            try {
                return Jwts.parser()
                    .verifyWith(kp.public as RSAPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (_: JwtException) {
                // Fall through to HMAC fallback
            }
        }

        // HMAC fallback (7-day migration)
        val lk = legacyKey
        if (lk != null) {
            return try {
                Jwts.parser()
                    .verifyWith(lk)
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

        throw IllegalStateException("No verification key configured")
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

    /**
     * JWKS endpoint data — expose RSA public key in JWK format.
     */
    fun getJwks(): Map<String, Any> {
        val kp = keyPair ?: return mapOf("keys" to emptyList<Any>())
        val rsaPub = kp.public as RSAPublicKey
        val jwk = mapOf(
            "kty" to "RSA",
            "kid" to securityProperties.jwt.keyId,
            "alg" to "RS256",
            "use" to "sig",
            "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(rsaPub.modulus.toByteArray()),
            "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(rsaPub.publicExponent.toByteArray())
        )
        return mapOf("keys" to listOf(jwk))
    }
}
