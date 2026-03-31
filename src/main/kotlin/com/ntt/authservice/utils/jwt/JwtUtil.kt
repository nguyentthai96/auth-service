package com.ntt.authservice.utils.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.bootstrap.encrypt.KeyProperties
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 * @reference : https://github.com/jwtk/jjwt?tab=readme-ov-file#signaturealgorithm-override
 **/
@Component
class JwtUtil {

    private final val keyProperties: KeyProperties = TODO("initialize me")

    @Value("\${jjwt.secret}")
    private lateinit var secret: String

    fun generateToken(username: String): String {
        val claims: Map<String, Any> = HashMap()
        return createToken(claims, username)
    }

    private fun createToken(claims: Map<String, Any>, subject: String): String {
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
            .signWith(getKey())
            .compact()
    }


    /**
     * Builds the secret key from the JWT secret stored in the application properties.
     * getSignKey from secret config in file yaml
     * @return the created secret key
     */
    private fun getKey(): SecretKey {
        // byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        // return Keys.hmacShaKeyFor(keyBytes);
        // return SIG.HS256.key().build();
        val keyBytes = Decoders.BASE64.decode(secret)
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun validateToken(token: String, username: String): Boolean {
        val claims = extractAllClaims(token)
        val tokenUsername = claims.subject
        return (tokenUsername == username && !isTokenExpired(token))
    }

    /**
     * Extracts all the claims from the token.
     * extractUsername from token
     */
    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    fun extractUsername(token: String): String {
        return extractClaim(token) { obj: Claims -> obj.subject }
    }

    fun extractExpiration(token: String): Date {
        return extractClaim(token) { obj: Claims -> obj.expiration }
    }

    private fun isTokenExpired(token: String): Boolean {
        val expiration = extractAllClaims(token).expiration
        return expiration.before(Date())
    }
}