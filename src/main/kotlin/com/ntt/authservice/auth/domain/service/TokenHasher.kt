package com.ntt.authservice.auth.domain.service

import java.security.MessageDigest
import java.util.Base64

/**
 * Token hashing utility — pure domain service.
 * Extracted from AuthService.hashToken().
 */
object TokenHasher {

    /**
     * Hash a token using SHA-256 and return Base64-encoded string.
     * Used for refresh token hashing, ETag generation.
     */
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray()))
    }

    /**
     * Hash input using SHA-256 and return lowercase hex-encoded string.
     * Used for recovery code hashing where hex format is required.
     */
    fun hashHex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
