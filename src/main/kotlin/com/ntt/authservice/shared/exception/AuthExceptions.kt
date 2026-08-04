package com.ntt.authservice.shared.exception

import org.springframework.http.HttpStatus

/**
 * Base exception for all auth service errors.
 * Maps to RFC 7807 ProblemDetail via GlobalExceptionHandler.
 */
open class AuthException(
    val errorCode: String,
    override val message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) : RuntimeException(message)

class ResourceNotFoundException(
    resource: String,
    identifier: Any
) : AuthException(
    errorCode = "${resource.uppercase()}_NOT_FOUND",
    message = "$resource not found with identifier: $identifier",
    httpStatus = HttpStatus.NOT_FOUND
)

class DuplicateResourceException(
    resource: String,
    field: String,
    value: Any
) : AuthException(
    errorCode = "${resource.uppercase()}_DUPLICATE",
    message = "$resource with $field '$value' already exists",
    httpStatus = HttpStatus.CONFLICT
)

class PermissionDeniedException(
    message: String = "You do not have permission to perform this action"
) : AuthException(
    errorCode = "PERMISSION_DENIED",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class WriteNotAllowedException(
    resource: String
) : AuthException(
    errorCode = "WRITE_NOT_ALLOWED",
    message = "Read-only access — write operations on '$resource' are not permitted",
    httpStatus = HttpStatus.FORBIDDEN
)

class AccountLockedException(
    val lockedUntilAt: java.time.Instant,
    val reason: String = "Too many failed login attempts"
) : AuthException(
    errorCode = "ACCOUNT_LOCKED",
    message = "Account is locked until ${lockedUntilAt}. Reason: $reason",
    httpStatus = HttpStatus.FORBIDDEN
)

class InvalidCredentialsException : AuthException(
    errorCode = "INVALID_CREDENTIALS",
    message = "Invalid username or password",
    httpStatus = HttpStatus.UNAUTHORIZED
)

class TokenExpiredException : AuthException(
    errorCode = "TOKEN_EXPIRED",
    message = "JWT token has expired",
    httpStatus = HttpStatus.UNAUTHORIZED
)

class PolicyEvaluationException(
    message: String = "Policy evaluation failed or timed out"
) : AuthException(
    errorCode = "POLICY_EVALUATION_FAILED",
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)
