package com.ntt.authservice.auth.domain.event

/**
 * Reasons for token validation failure — used in TokenValidationFailedEvent.
 * Mapped to structured logging levels and audit event types.
 *
 * FR-012: Validation failure event recording.
 */
enum class ValidationFailureReason {
    /** Token JTI found in blacklist (revoked). WARN level. */
    BLACKLISTED,
    /** JWT signature verification failed (tampering or wrong key). WARN level. */
    SIGNATURE_INVALID,
    /** Audience claim does not match configured value. WARN level. */
    AUDIENCE_MISMATCH,
    /** Token type not allowed as access token (e.g., refresh, mfa). WARN level. */
    TYPE_REJECTED
}
