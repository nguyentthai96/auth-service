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
 * Supports RS256 (primary) with key rotation overlap (previous key) and HMAC-SHA256 fallback.
 *
 * FR-005: Clock skew tolerance (configurable, default 60s).
 * FR-006: Dual-key rotation — current RS256 → previous RS256 → HMAC legacy.
 * FR-013: Audience claim in generated access tokens.
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

    /**
     * Previous RS256 key pair for key rotation overlap period (FR-006).
     * Only loaded when previousPublicKeyPath is configured.
     * Used for validation fallback — never for signing.
     */
    private val previousKeyPair: KeyPair? by lazy {
        val props = securityProperties.jwt
        if (props.previousPublicKeyPath.isNotBlank()) {
            try {
                loadPublicKeyOnly(props.previousPublicKeyPath)
            } catch (e: Exception) {
                log.warn("Failed to load previous RSA public key: {}", e.message)
                null
            }
        } else {
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
     * Load only the public key (for previous key rotation — no private key needed for verification).
     */
    private fun loadPublicKeyOnly(publicKeyPath: String): KeyPair {
        val kf = KeyFactory.getInstance("RSA")
        val publicKeyPem = Files.readString(Path.of(publicKeyPath))
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val publicKey = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPem)))
        log.info("Previous RS256 public key loaded (kid={})", securityProperties.jwt.previousKeyId)
        // KeyPair with null private key — only used for verification
        return KeyPair(publicKey, null)
    }

    /**
     * Generate access token with embedded permissions.
     * FR-013: Adds audience claim when configured.
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

        // FR-013: Audience claim (when configured)
        if (securityProperties.jwt.audience.isNotBlank()) {
            builder.claim("aud", securityProperties.jwt.audience)
        }

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
     * FR-005: Clock skew tolerance applied to all parsers.
     * FR-006: Tries current RS256 → previous RS256 (key rotation) → HMAC (legacy migration).
     */
    fun parseToken(token: String): Claims {
        val clockSkew = securityProperties.jwt.clockSkewSeconds

        // Try current RS256 key
        val kp = keyPair
        if (kp != null) {
            try {
                return Jwts.parser()
                    .verifyWith(kp.public as RSAPublicKey)
                    .clockSkewSeconds(clockSkew)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (_: JwtException) {
                // Fall through to previous key or HMAC fallback
            }
        }

        // FR-006: Try previous RS256 key (key rotation overlap)
        val prevKp = previousKeyPair
        if (prevKp != null) {
            try {
                return Jwts.parser()
                    .verifyWith(prevKp.public as RSAPublicKey)
                    .clockSkewSeconds(clockSkew)
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
                    .clockSkewSeconds(clockSkew)
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
     * JWKS endpoint data — expose RSA public key(s) in JWK format.
     * FR-006: Returns both current and previous keys during key rotation overlap.
     */
    fun getJwks(): Map<String, Any> {
        val keys = mutableListOf<Map<String, String>>()

        // Current key
        val kp = keyPair
        if (kp != null) {
            keys.add(buildJwk(kp.public as RSAPublicKey, securityProperties.jwt.keyId))
        }

        // Previous key (key rotation overlap)
        val prevKp = previousKeyPair
        if (prevKp != null && securityProperties.jwt.previousKeyId.isNotBlank()) {
            keys.add(buildJwk(prevKp.public as RSAPublicKey, securityProperties.jwt.previousKeyId))
        }

        return mapOf("keys" to keys)
    }

    /**
     * Build a single JWK entry from an RSA public key.
     */
    private fun buildJwk(rsaPub: RSAPublicKey, kid: String): Map<String, String> {
        return mapOf(
            "kty" to "RSA",
            "kid" to kid,
            "alg" to "RS256",
            "use" to "sig",
            "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(rsaPub.modulus.toByteArray()),
            "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(rsaPub.publicExponent.toByteArray())
        )
    }
}
