package com.ntt.authservice.auth.domain.event

/**
 * Discriminator for TokenRevokedEvent — identifies the revocation context.
 */
enum class RevocationType {
    ROTATION,           // Old token revoked during refresh (rotation)
    LOGOUT,             // User-initiated logout
    ADMIN_REVOKE,       // Admin-initiated revocation
    BULK_REVOKE         // Revoke all sessions for user
}
