package com.ntt.accountservice.shared.exception

import com.ntt.basecore.exception.base.ErrorCodeBase
import org.springframework.http.HttpStatus

/**
 * Account service error codes — implements base-core ErrorCodeBase.
 * Pattern: Same as AuthErrorCode — enum with errorCode, msgCode, httpStatus.
 */
enum class AccountErrorCode(
    private val errorCode: String,
    private val msgCode: String,
    private val description: String,
    val httpStatus: HttpStatus
) {
    PROFILE_NOT_FOUND("ACCT_001", "account.profile_not_found", "User profile not found", HttpStatus.NOT_FOUND),
    PROFILE_ALREADY_EXISTS("ACCT_002", "account.profile_already_exists", "User profile already exists", HttpStatus.CONFLICT),
    CONTACT_VERIFICATION_REQUIRED("ACCT_003", "account.contact_verification_required", "Contact change requires verification", HttpStatus.PRECONDITION_REQUIRED),
    INVALID_PREFERENCE_FORMAT("ACCT_004", "account.invalid_preference_format", "Invalid preference format or category", HttpStatus.BAD_REQUEST),
    DEVICE_NOT_FOUND("ACCT_005", "account.device_not_found", "Device not found", HttpStatus.NOT_FOUND),
    MAX_DEVICES_REACHED("ACCT_006", "account.max_devices_reached", "Maximum devices per user reached", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_CONTACT_TYPE("ACCT_007", "account.invalid_contact_type", "Invalid contact type", HttpStatus.BAD_REQUEST),
    GENERAL_ERROR("ACCT_008", "account.general_error", "Account service error", HttpStatus.INTERNAL_SERVER_ERROR);

    private val delegate = object : ErrorCodeBase(errorCode, msgCode, description) {}

    fun getErrorCode(): String = errorCode
    fun toErrorCodeBase(): ErrorCodeBase = delegate
    override fun toString(): String = errorCode
}
