package com.ntt.authservice.auth.domain.event

/**
 * Classification of login failure reasons — mapped 1:1 with LoginHandler exceptions.
 * Used in UserLoginFailedEvent for structured failure analysis.
 *
 * FR-003: Failure reason classification.
 *
 * Follows ValidationFailureReason pattern — enum with KDoc per value.
 */
enum class LoginFailureReason {
    /** User not found or password mismatch (InvalidCredentialsException). */
    INVALID_CREDENTIALS,
    /** Account is locked — temporary or permanent (AccountLockedException). */
    ACCOUNT_LOCKED,
    /** Account is disabled (reserved — currently filtered by findByUsernameAndActive). */
    ACCOUNT_DISABLED,
    /** CAPTCHA required but not provided (CaptchaRequiredException). */
    CAPTCHA_REQUIRED,
    /** CAPTCHA verification failed (CaptchaFailedException). */
    CAPTCHA_FAILED,
    /** Password has expired (PasswordExpiredException). */
    PASSWORD_EXPIRED,
    /** Login rate limit exceeded (RateLimitExceededException). */
    RATE_LIMITED,
    /** MFA required (reserved — MFA checkpoint returns early, not exception). */
    MFA_REQUIRED,
    /** Fallback for any unexpected AuthException subclass. */
    UNKNOWN
}
