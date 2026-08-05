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
     */
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray()))
    }
}
