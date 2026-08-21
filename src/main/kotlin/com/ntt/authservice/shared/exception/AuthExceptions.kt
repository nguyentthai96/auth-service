package com.ntt.authservice.shared.exception

import com.ntt.basecore.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * Base exception for all auth service errors.
 * Bridge pattern: extends BusinessException (base-core) while preserving
 * ProblemDetail response and per-exception HTTP status.
 */
open class AuthException(
    val authError: AuthErrorCode,
    override val message: String = authError.toErrorCodeBase().getDesc() ?: "",
    val httpStatus: HttpStatus = authError.httpStatus
) : BusinessException(authError.toErrorCodeBase())

class ResourceNotFoundException(
    resource: String,
    identifier: Any
) : AuthException(
    authError = AuthErrorCode.RESOURCE_NOT_FOUND,
    message = "$resource not found with identifier: $identifier",
    httpStatus = HttpStatus.NOT_FOUND
)

class DuplicateResourceException(
    resource: String,
    field: String,
    value: Any
) : AuthException(
    authError = AuthErrorCode.DUPLICATE_RESOURCE,
    message = "$resource with $field '$value' already exists",
    httpStatus = HttpStatus.CONFLICT
)

class PermissionDeniedException(
    message: String = "You do not have permission to perform this action"
) : AuthException(
    authError = AuthErrorCode.PERMISSION_DENIED,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class WriteNotAllowedException(
    resource: String
) : AuthException(
    authError = AuthErrorCode.WRITE_NOT_ALLOWED,
    message = "Read-only access — write operations on '$resource' are not permitted",
    httpStatus = HttpStatus.FORBIDDEN
)

class AccountLockedException(
    val lockedUntilAt: java.time.Instant,
    val reason: String = "Too many failed login attempts"
) : AuthException(
    authError = AuthErrorCode.ACCOUNT_LOCKED,
    message = "Account is locked until $lockedUntilAt. Reason: $reason",
    httpStatus = HttpStatus.FORBIDDEN
)

class InvalidCredentialsException : AuthException(
    authError = AuthErrorCode.INVALID_CREDENTIALS,
    message = "Invalid username or password",
    httpStatus = HttpStatus.UNAUTHORIZED
)

class TokenExpiredException : AuthException(
    authError = AuthErrorCode.TOKEN_EXPIRED,
    message = "JWT token has expired",
    httpStatus = HttpStatus.UNAUTHORIZED
)

class PolicyEvaluationException(
    message: String = "Policy evaluation failed or timed out"
) : AuthException(
    authError = AuthErrorCode.POLICY_EVALUATION_FAILED,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class EventStorePersistException(
    message: String = "Failed to persist event to event store"
) : AuthException(
    authError = AuthErrorCode.EVENT_STORE_PERSIST_FAILED,
    message = message,
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
)

class EventNotFoundException(
    aggregateType: String,
    aggregateId: Long
) : AuthException(
    authError = AuthErrorCode.EVENT_NOT_FOUND,
    message = "No events found for $aggregateType with id: $aggregateId",
    httpStatus = HttpStatus.NOT_FOUND
)

// --- Inter-service Auth Exceptions (FR-021) ---

class ServiceTokenInvalidException(
    message: String = "Invalid or expired service authentication token"
) : AuthException(
    authError = AuthErrorCode.INVALID_SERVICE_TOKEN,
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class ServiceInsufficientScopeException(
    serviceName: String,
    requiredScope: String
) : AuthException(
    authError = AuthErrorCode.INSUFFICIENT_SCOPE,
    message = "Service '$serviceName' does not have scope '$requiredScope'",
    httpStatus = HttpStatus.FORBIDDEN
)

class ServiceNotRegisteredException(
    serviceName: String
) : AuthException(
    authError = AuthErrorCode.SERVICE_NOT_REGISTERED,
    message = "Service '$serviceName' is not registered for inter-service communication",
    httpStatus = HttpStatus.FORBIDDEN
)
