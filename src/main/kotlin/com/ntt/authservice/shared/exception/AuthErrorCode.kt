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
    MFA_RATE_LIMITED("AUTH_019", "auth.mfa_rate_limited", "MFA rate limit exceeded — too many failed attempts", HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMITED("AUTH_020", "auth.rate_limited", "Too many login attempts", HttpStatus.TOO_MANY_REQUESTS),
    SESSION_LIMIT_EXCEEDED("AUTH_021", "auth.session_limit", "Maximum active sessions exceeded", HttpStatus.CONFLICT),

    // --- E2EE (End-to-End Encryption) Error Codes — AUTH_030~039 ---
    E2EE_TIME_SKEW("AUTH_030", "auth.e2ee_time_skew", "Request timestamp out of tolerance window", HttpStatus.BAD_REQUEST),
    E2EE_VERSION_UNKNOWN("AUTH_031", "auth.e2ee_version_unknown", "Unknown cipher version", HttpStatus.BAD_REQUEST),
    E2EE_CONTEXT_MISMATCH("AUTH_032", "auth.e2ee_context_mismatch", "AAD context mismatch — possible cut-and-paste attack", HttpStatus.FORBIDDEN),
    E2EE_REPLAY_DETECTED("AUTH_033", "auth.e2ee_replay_detected", "Duplicate nonce — replay attack detected", HttpStatus.CONFLICT),
    E2EE_VERSION_SUNSET("AUTH_034", "auth.e2ee_version_sunset", "Cipher version past sunset deadline", HttpStatus.UPGRADE_REQUIRED),
    E2EE_DECRYPT_FAILED("AUTH_035", "auth.e2ee_decrypt_failed", "Decryption failed — corrupted or tampered payload", HttpStatus.INTERNAL_SERVER_ERROR),
    E2EE_KMS_UNAVAILABLE("AUTH_036", "auth.e2ee_kms_unavailable", "KMS unavailable and no cached DEK", HttpStatus.SERVICE_UNAVAILABLE),
    E2EE_KEY_EXPIRED("AUTH_037", "auth.e2ee_key_expired", "Key session expired — re-exchange required", HttpStatus.UNAUTHORIZED),
    E2EE_DEVICE_UNREGISTERED("AUTH_038", "auth.e2ee_device_unregistered", "Device not registered for E2EE", HttpStatus.FORBIDDEN),
    E2EE_MAX_DEVICES("AUTH_039", "auth.e2ee_max_devices", "Maximum devices per user exceeded", HttpStatus.TOO_MANY_REQUESTS),

    // --- Anonymous Session Error Codes — AUTH_040~044 ---
    ANONYMOUS_SESSION_EXPIRED("AUTH_040", "auth.anonymous_session_expired", "Anonymous session expired or not found", HttpStatus.NOT_FOUND),
    ANONYMOUS_DATA_LIMIT_EXCEEDED("AUTH_041", "auth.anonymous_data_limit_exceeded", "Anonymous session data limit exceeded", HttpStatus.PAYLOAD_TOO_LARGE),
    ANONYMOUS_PROMOTION_CONFLICT("AUTH_042", "auth.anonymous_promotion_conflict", "Anonymous session promotion conflict", HttpStatus.CONFLICT),
    ANONYMOUS_RATE_LIMITED("AUTH_043", "auth.anonymous_rate_limited", "Anonymous token creation rate limited", HttpStatus.TOO_MANY_REQUESTS),
    ANONYMOUS_MAX_RENEWALS("AUTH_044", "auth.anonymous_max_renewals", "Anonymous token maximum renewals exceeded", HttpStatus.TOO_MANY_REQUESTS),

    // --- Event Sourcing Error Codes — AUTH_050~053 ---
    EVENT_STORE_PERSIST_FAILED("AUTH_050", "auth.event_store_persist_failed", "Event store write failure", HttpStatus.INTERNAL_SERVER_ERROR),
    OUTBOX_PUBLISH_FAILED("AUTH_051", "auth.outbox_publish_failed", "Outbox Kafka publish failure", HttpStatus.INTERNAL_SERVER_ERROR),
    EVENT_NOT_FOUND("AUTH_052", "auth.event_not_found", "Event not found", HttpStatus.NOT_FOUND),
    OUTBOX_MAX_RETRIES_EXCEEDED("AUTH_053", "auth.outbox_max_retries", "Outbox max retries exceeded", HttpStatus.INTERNAL_SERVER_ERROR),

    // --- Inter-service Auth Error Codes — AUTH_060~062 (FR-021) ---
    INVALID_SERVICE_TOKEN("AUTH_060", "auth.invalid_service_token", "Invalid or expired service authentication token", HttpStatus.UNAUTHORIZED),
    INSUFFICIENT_SCOPE("AUTH_061", "auth.insufficient_scope", "Service does not have sufficient scope for this operation", HttpStatus.FORBIDDEN),
    SERVICE_NOT_REGISTERED("AUTH_062", "auth.service_not_registered", "Service is not registered for inter-service communication", HttpStatus.FORBIDDEN);

    /**
     * Bridge to base-core ErrorCodeBase via delegation.
     */
    private val delegate = object : ErrorCodeBase(errorCode, msgCode, description) {}

    fun getErrorCode(): String = errorCode
    fun toErrorCodeBase(): ErrorCodeBase = delegate
    override fun toString(): String = errorCode
}
