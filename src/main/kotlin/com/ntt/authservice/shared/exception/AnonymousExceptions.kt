package com.ntt.authservice.shared.exception

import org.springframework.http.HttpStatus

/**
 * Exception classes for anonymous session features.
 * Each class extends AuthException with the corresponding AuthErrorCode (AUTH_040-044).
 * Handled automatically by GlobalExceptionHandler via AuthException hierarchy.
 */

class AnonymousSessionExpiredException(
    sessionId: String
) : AuthException(
    authError = AuthErrorCode.ANONYMOUS_SESSION_EXPIRED,
    message = "Anonymous session expired or not found: $sessionId",
    httpStatus = HttpStatus.NOT_FOUND
)

class AnonymousDataLimitExceededException(
    val currentSize: Long,
    val maxSize: Long
) : AuthException(
    authError = AuthErrorCode.ANONYMOUS_DATA_LIMIT_EXCEEDED,
    message = "Anonymous session data limit exceeded: current=$currentSize bytes, max=$maxSize bytes",
    httpStatus = HttpStatus.PAYLOAD_TOO_LARGE
)

class AnonymousPromotionConflictException(
    sessionId: String
) : AuthException(
    authError = AuthErrorCode.ANONYMOUS_PROMOTION_CONFLICT,
    message = "Anonymous session promotion conflict — session $sessionId is being promoted by another request",
    httpStatus = HttpStatus.CONFLICT
)

class AnonymousRateLimitedException(
    val retryAfterSeconds: Long
) : AuthException(
    authError = AuthErrorCode.ANONYMOUS_RATE_LIMITED,
    message = "Anonymous token creation rate limited — retry after $retryAfterSeconds seconds",
    httpStatus = HttpStatus.TOO_MANY_REQUESTS
)

class AnonymousMaxRenewalsException(
    val maxRenewals: Int
) : AuthException(
    authError = AuthErrorCode.ANONYMOUS_MAX_RENEWALS,
    message = "Anonymous token maximum renewals exceeded: max=$maxRenewals",
    httpStatus = HttpStatus.TOO_MANY_REQUESTS
)
