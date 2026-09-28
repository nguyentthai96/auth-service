package com.ntt.authservice.auth.application.service

import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.model.User
import org.springframework.stereotype.Service

/**
 * Identity Resolver — App-level routing for optimal auth query performance.
 *
 * Fast path (99.9%): Regex classifier → targeted Single Index Scan (< 0.1ms)
 * Safe fallback (0.1%): OR query → BitmapOr scan for edge cases
 *
 * Change: user-identity-dual-key | FR-007, FR-008
 */
@Service
class IdentityResolver(
    private val userPort: UserPort
) {

    /**
     * Resolve a user identity from a login identifier.
     * Accepts: username, email, or phone number.
     *
     * Routing strategy:
     * - Contains '@' → email lookup
     * - Starts with '+' or all digits (8-15 chars) → phone lookup
     * - Otherwise → username lookup
     * - Fallback: OR query across all three fields
     */
    fun resolve(identifier: String): User? {
        val clean = identifier.trim()
        if (clean.isBlank()) return null

        // FAST PATH: App-level routing → Single Index Scan
        val fastResult = when {
            clean.contains('@') ->
                userPort.findByEmailAndActive(clean.lowercase())
            isPhoneNumber(clean) ->
                userPort.findByPhoneAndActive(clean)
            else ->
                userPort.findByUsernameAndActive(clean.lowercase())
        }
        if (fastResult != null) return fastResult

        // SAFE FALLBACK: OR query for edge cases
        return userPort.findByIdentifierAny(clean)
    }

    companion object {
        /**
         * Username validation pattern: lowercase alpha start, alphanumeric + underscore, 3-30 chars.
         * Enforced at registration to maintain identifier space separation.
         */
        val USERNAME_PATTERN = Regex("^[a-z][a-z0-9_]{2,29}$")

        /**
         * Check if the input looks like a phone number.
         * Matches: starts with '+' or consists entirely of digits (8-15 chars).
         */
        fun isPhoneNumber(input: String): Boolean {
            if (input.startsWith('+')) return true
            return input.length in 8..15 && input.all { it.isDigit() }
        }

        /**
         * Validate a username against the allowed pattern.
         * Must not contain '@' (reserved for email) or start with '+' (reserved for phone).
         */
        fun isValidUsername(username: String): Boolean {
            return USERNAME_PATTERN.matches(username)
        }
    }
}
