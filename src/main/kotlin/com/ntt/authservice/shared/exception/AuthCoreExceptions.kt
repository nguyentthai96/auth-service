package com.ntt.authservice.shared.exception

import org.springframework.http.HttpStatus

/**
 * Auth Core Features exceptions — MFA, SSO, CAPTCHA, Password Policy.
 * All extend AuthException (→ BusinessException) for unified handling.
 */

// --- MFA Exceptions ---

class MfaCodeInvalidException(
    message: String = "Invalid MFA verification code"
) : AuthException(
    authError = AuthErrorCode.MFA_CODE_INVALID,
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class MfaTokenExpiredException(
    message: String = "MFA session token has expired"
) : AuthException(
    authError = AuthErrorCode.MFA_TOKEN_EXPIRED,
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class MfaMaxAttemptsException(
    message: String = "Maximum MFA verification attempts exceeded"
) : AuthException(
    authError = AuthErrorCode.MFA_MAX_ATTEMPTS,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class MfaAccountLockedException(
    val retryAfterSeconds: Long,
    val lockType: String,
    message: String = "Account temporarily locked due to too many failed attempts"
) : AuthException(
    authError = AuthErrorCode.MFA_RATE_LIMITED,
    message = message,
    httpStatus = HttpStatus.TOO_MANY_REQUESTS
)

class TotpNotSetupException(
    message: String = "TOTP authenticator is not configured for this account"
) : AuthException(
    authError = AuthErrorCode.MFA_CODE_INVALID,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

// --- CAPTCHA Exceptions ---

class CaptchaRequiredException(
    message: String = "CAPTCHA verification required due to multiple failed login attempts"
) : AuthException(
    authError = AuthErrorCode.CAPTCHA_REQUIRED,
    message = message,
    httpStatus = HttpStatus.PRECONDITION_REQUIRED
)

class CaptchaFailedException(
    message: String = "CAPTCHA verification failed"
) : AuthException(
    authError = AuthErrorCode.CAPTCHA_FAILED,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

// --- SSO Exceptions ---

class SsoTokenInvalidException(
    message: String = "SSO token exchange failed — invalid or expired authorization code"
) : AuthException(
    authError = AuthErrorCode.SSO_TOKEN_INVALID,
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class SsoUserNotProvisionedException(
    message: String = "SSO user not provisioned — auto-provisioning is disabled for this domain"
) : AuthException(
    authError = AuthErrorCode.SSO_USER_NOT_PROVISIONED,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class SsoIdentityConflictException(
    message: String = "SSO identity is already linked to another user account"
) : AuthException(
    authError = AuthErrorCode.SSO_IDENTITY_CONFLICT,
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

class CannotUnlinkLastIdentityException(
    message: String = "Cannot unlink the last SSO identity — set a password first"
) : AuthException(
    authError = AuthErrorCode.SSO_TOKEN_INVALID,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

class SsoProviderTimeoutException(
    message: String = "SSO identity provider did not respond within timeout"
) : AuthException(
    authError = AuthErrorCode.SSO_TOKEN_INVALID,
    message = message,
    httpStatus = HttpStatus.GATEWAY_TIMEOUT
)

// --- Password Policy Exceptions ---

class PasswordRecentlyUsedException(
    message: String = "This password has been used recently — choose a different password"
) : AuthException(
    authError = AuthErrorCode.PASSWORD_POLICY_VIOLATION,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

class PasswordExpiredException(
    message: String = "Your password has expired — please change it"
) : AuthException(
    authError = AuthErrorCode.PASSWORD_EXPIRED,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class PasswordPolicyViolationException(
    message: String = "Password does not meet complexity requirements"
) : AuthException(
    authError = AuthErrorCode.PASSWORD_POLICY_VIOLATION,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)
