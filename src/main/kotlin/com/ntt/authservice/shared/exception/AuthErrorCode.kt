package com.ntt.authservice.shared.exception

import com.ntt.basecore.exception.base.ErrorCodeBase
import org.springframework.http.HttpStatus

/**
 * Auth service error codes — implements base-core ErrorCodeBase.
 * Each error code carries its HTTP status for RFC 7807 ProblemDetail mapping.
 */
enum class AuthErrorCode(
    private val errorCode: String,
    private val msgCode: String,
    private val description: String,
    val httpStatus: HttpStatus
) {
    INVALID_CREDENTIALS("AUTH_001", "auth.invalid_credentials", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_002", "auth.account_locked", "Account is locked due to failed login attempts", HttpStatus.FORBIDDEN),
    TOKEN_EXPIRED("AUTH_003", "auth.token_expired", "JWT token has expired", HttpStatus.UNAUTHORIZED),
    PERMISSION_DENIED("AUTH_004", "auth.permission_denied", "Insufficient permissions", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("AUTH_005", "auth.resource_not_found", "Resource not found", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE("AUTH_006", "auth.duplicate_resource", "Resource already exists", HttpStatus.CONFLICT),
    CAPTCHA_REQUIRED("AUTH_007", "auth.captcha_required", "CAPTCHA verification required", HttpStatus.PRECONDITION_REQUIRED),
    CAPTCHA_FAILED("AUTH_008", "auth.captcha_failed", "CAPTCHA verification failed", HttpStatus.BAD_REQUEST),
    POLICY_EVALUATION_FAILED("AUTH_009", "auth.policy_evaluation_failed", "Policy evaluation failed", HttpStatus.FORBIDDEN),
    WRITE_NOT_ALLOWED("AUTH_010", "auth.write_not_allowed", "Write operation not allowed", HttpStatus.FORBIDDEN),
    MFA_CODE_INVALID("AUTH_011", "auth.mfa_code_invalid", "Invalid MFA verification code", HttpStatus.UNAUTHORIZED),
    MFA_TOKEN_EXPIRED("AUTH_012", "auth.mfa_token_expired", "MFA session token has expired", HttpStatus.UNAUTHORIZED),
    MFA_MAX_ATTEMPTS("AUTH_013", "auth.mfa_max_attempts", "Maximum MFA verification attempts exceeded", HttpStatus.FORBIDDEN),
    SSO_TOKEN_INVALID("AUTH_014", "auth.sso_token_invalid", "SSO token exchange failed", HttpStatus.UNAUTHORIZED),
    SSO_USER_NOT_PROVISIONED("AUTH_015", "auth.sso_user_not_provisioned", "SSO user not provisioned", HttpStatus.FORBIDDEN),
    SSO_IDENTITY_CONFLICT("AUTH_016", "auth.sso_identity_conflict", "SSO identity already linked", HttpStatus.CONFLICT),
    PASSWORD_POLICY_VIOLATION("AUTH_017", "auth.password_policy_violation", "Password does not meet requirements", HttpStatus.BAD_REQUEST),
    PASSWORD_EXPIRED("AUTH_018", "auth.password_expired", "Password has expired", HttpStatus.FORBIDDEN),
    MFA_RATE_LIMITED("AUTH_019", "auth.mfa_rate_limited", "MFA rate limit exceeded — too many failed attempts", HttpStatus.TOO_MANY_REQUESTS);

    /**
     * Bridge to base-core ErrorCodeBase via delegation.
     */
    private val delegate = object : ErrorCodeBase(errorCode, msgCode, description) {}

    fun getErrorCode(): String = errorCode
    fun toErrorCodeBase(): ErrorCodeBase = delegate
    override fun toString(): String = errorCode
}
