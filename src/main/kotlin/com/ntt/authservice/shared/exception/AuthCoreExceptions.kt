package com.ntt.authservice.shared.exception

import org.springframework.http.HttpStatus

/**
 * Auth Core Features exceptions — MFA, SSO, CAPTCHA, Password Policy.
 * All extend AuthException for unified RFC 7807 handling.
 */

// --- MFA Exceptions ---

class MfaCodeInvalidException(
    message: String = "Invalid MFA verification code"
) : AuthException(
    errorCode = "MFA_CODE_INVALID",
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class MfaTokenExpiredException(
    message: String = "MFA session token has expired"
) : AuthException(
    errorCode = "MFA_TOKEN_EXPIRED",
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class MfaMaxAttemptsException(
    message: String = "Maximum MFA verification attempts exceeded"
) : AuthException(
    errorCode = "MFA_MAX_ATTEMPTS",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class TotpNotSetupException(
    message: String = "TOTP authenticator is not configured for this account"
) : AuthException(
    errorCode = "TOTP_NOT_SETUP",
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

// --- CAPTCHA Exceptions ---

class CaptchaRequiredException(
    message: String = "CAPTCHA verification required due to multiple failed login attempts"
) : AuthException(
    errorCode = "CAPTCHA_REQUIRED",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class CaptchaFailedException(
    message: String = "CAPTCHA verification failed"
) : AuthException(
    errorCode = "CAPTCHA_FAILED",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

// --- SSO Exceptions ---

class SsoTokenInvalidException(
    message: String = "SSO token exchange failed — invalid or expired authorization code"
) : AuthException(
    errorCode = "SSO_TOKEN_INVALID",
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class SsoUserNotProvisionedException(
    message: String = "SSO user not provisioned — auto-provisioning is disabled for this domain"
) : AuthException(
    errorCode = "SSO_USER_NOT_PROVISIONED",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class SsoIdentityConflictException(
    message: String = "SSO identity is already linked to another user account"
) : AuthException(
    errorCode = "SSO_IDENTITY_CONFLICT",
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class CannotUnlinkLastIdentityException(
    message: String = "Cannot unlink the last SSO identity — set a password first"
) : AuthException(
    errorCode = "CANNOT_UNLINK_LAST_IDENTITY",
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

class SsoProviderTimeoutException(
    message: String = "SSO identity provider did not respond within timeout"
) : AuthException(
    errorCode = "SSO_PROVIDER_TIMEOUT",
    message = message,
    httpStatus = HttpStatus.GATEWAY_TIMEOUT
)

// --- Password Policy Exceptions ---

class PasswordRecentlyUsedException(
    message: String = "This password has been used recently — choose a different password"
) : AuthException(
    errorCode = "PASSWORD_RECENTLY_USED",
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

class PasswordExpiredException(
    message: String = "Your password has expired — please change it"
) : AuthException(
    errorCode = "PASSWORD_EXPIRED",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class PasswordPolicyViolationException(
    message: String = "Password does not meet complexity requirements"
) : AuthException(
    errorCode = "PASSWORD_POLICY_VIOLATION",
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)
