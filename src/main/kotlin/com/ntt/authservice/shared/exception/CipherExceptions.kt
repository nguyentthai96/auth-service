package com.ntt.authservice.shared.exception

import org.springframework.http.HttpStatus

/**
 * E2EE (End-to-End Encryption) exception classes.
 * Each maps to an AuthErrorCode (AUTH_030~039) for RFC 7807 ProblemDetail response.
 */

// --- Timestamp & Replay ---

class CipherTimeSkewException(
    message: String = "Request timestamp out of tolerance window"
) : AuthException(
    authError = AuthErrorCode.E2EE_TIME_SKEW,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

class CipherReplayDetectedException(
    val nonce: String,
    message: String = "Duplicate nonce '$nonce' — replay attack detected"
) : AuthException(
    authError = AuthErrorCode.E2EE_REPLAY_DETECTED,
    message = message,
    httpStatus = HttpStatus.CONFLICT
)

// --- Context & Integrity ---

class CipherContextMismatchException(
    message: String = "AAD context mismatch — possible cut-and-paste attack"
) : AuthException(
    authError = AuthErrorCode.E2EE_CONTEXT_MISMATCH,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

// --- Version Negotiation ---

class CipherVersionUnknownException(
    val version: String,
    message: String = "Unknown cipher version: $version"
) : AuthException(
    authError = AuthErrorCode.E2EE_VERSION_UNKNOWN,
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST
)

class CipherVersionSunsetException(
    val version: String,
    val sunsetDate: String,
    message: String = "Cipher version '$version' past sunset deadline ($sunsetDate)"
) : AuthException(
    authError = AuthErrorCode.E2EE_VERSION_SUNSET,
    message = message,
    httpStatus = HttpStatus.UPGRADE_REQUIRED
)

// --- Crypto Failures ---

class CipherDecryptFailedException(
    message: String = "Decryption failed — corrupted or tampered payload"
) : AuthException(
    authError = AuthErrorCode.E2EE_DECRYPT_FAILED,
    message = message,
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
)

class CipherKmsUnavailableException(
    message: String = "KMS unavailable and no cached DEK"
) : AuthException(
    authError = AuthErrorCode.E2EE_KMS_UNAVAILABLE,
    message = message,
    httpStatus = HttpStatus.SERVICE_UNAVAILABLE
)

// --- Key Session ---

class CipherKeyExpiredException(
    val keyId: String,
    message: String = "Key session '$keyId' expired — re-exchange required"
) : AuthException(
    authError = AuthErrorCode.E2EE_KEY_EXPIRED,
    message = message,
    httpStatus = HttpStatus.UNAUTHORIZED
)

class CipherDeviceUnregisteredException(
    val deviceId: String,
    message: String = "Device '$deviceId' not registered for E2EE"
) : AuthException(
    authError = AuthErrorCode.E2EE_DEVICE_UNREGISTERED,
    message = message,
    httpStatus = HttpStatus.FORBIDDEN
)

class CipherMaxDevicesException(
    val maxDevices: Int,
    message: String = "Maximum devices per user ($maxDevices) exceeded"
) : AuthException(
    authError = AuthErrorCode.E2EE_MAX_DEVICES,
    message = message,
    httpStatus = HttpStatus.TOO_MANY_REQUESTS
)
