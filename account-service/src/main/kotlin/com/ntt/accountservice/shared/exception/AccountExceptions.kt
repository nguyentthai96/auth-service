package com.ntt.accountservice.shared.exception

import com.ntt.basecore.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * Base exception for all account service errors.
 * Bridge pattern: extends BusinessException (base-core) while preserving
 * ProblemDetail response and per-exception HTTP status.
 */
open class AccountException(
    val accountError: AccountErrorCode,
    override val message: String = accountError.toErrorCodeBase().getDesc() ?: "",
    val httpStatus: HttpStatus = accountError.httpStatus
) : BusinessException(accountError.toErrorCodeBase())

class ProfileNotFoundException(
    userId: Long
) : AccountException(
    accountError = AccountErrorCode.PROFILE_NOT_FOUND,
    message = "Profile not found for userId: $userId",
    httpStatus = HttpStatus.NOT_FOUND
)

class DeviceNotFoundException(
    deviceId: Long
) : AccountException(
    accountError = AccountErrorCode.DEVICE_NOT_FOUND,
    message = "Device not found with id: $deviceId",
    httpStatus = HttpStatus.NOT_FOUND
)

class MaxDevicesReachedException(
    val maxDevices: Int
) : AccountException(
    accountError = AccountErrorCode.MAX_DEVICES_REACHED,
    message = "Maximum devices ($maxDevices) reached for user",
    httpStatus = HttpStatus.TOO_MANY_REQUESTS
)

class InvalidPreferenceFormatException(
    detail: String
) : AccountException(
    accountError = AccountErrorCode.INVALID_PREFERENCE_FORMAT,
    message = "Invalid preference format: $detail",
    httpStatus = HttpStatus.BAD_REQUEST
)
