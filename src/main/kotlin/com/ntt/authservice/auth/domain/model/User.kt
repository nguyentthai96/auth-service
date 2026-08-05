package com.ntt.authservice.auth.domain.model

import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import java.time.Instant

/**
 * User domain model — pure Kotlin, NO framework imports.
 * Encapsulates user identity, authentication state, and MFA configuration.
 */
class User(
    val id: UserId,
    val username: String,
    val email: Email,
    val fullName: String,
    val phone: String? = null,
    val avatarUrl: String? = null,
    var passwordHash: PasswordHash,
    var status: UserStatus = UserStatus.Active,
    var failedLoginCount: Int = 0,
    var lockedUntilAt: Instant? = null,
    val mfaEnabled: Boolean = false,
    val mfaMethod: String = "NONE",
    val trustedDeviceHash: String? = null,
    val passwordChangedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
) {

    /**
     * Check if the user's account lock has expired.
     */
    fun isLockExpired(now: Instant = Instant.now()): Boolean = when (val s = status) {
        is UserStatus.Locked -> s.until.isBefore(now)
        else -> false
    }

    /**
     * Unlock the user after lock expiry.
     */
    fun unlockIfExpired(now: Instant = Instant.now()) {
        if (isLockExpired(now)) {
            status = UserStatus.Active
            failedLoginCount = 0
            lockedUntilAt = null
        }
    }

    /**
     * Record a failed login attempt and lock if threshold exceeded.
     */
    fun recordFailedLogin(maxAttempts: Int, lockDurationSeconds: Long) {
        failedLoginCount += 1
        if (failedLoginCount >= maxAttempts) {
            val lockUntil = Instant.now().plusSeconds(lockDurationSeconds)
            status = UserStatus.Locked(lockUntil)
            lockedUntilAt = lockUntil
        }
    }

    /**
     * Reset failed login count after successful authentication.
     */
    fun resetFailedLogins() {
        failedLoginCount = 0
    }

    /**
     * Check if MFA is required and the device is not trusted.
     */
    fun requiresMfa(deviceHash: String?): Boolean {
        if (!mfaEnabled || mfaMethod == "NONE") return false
        return deviceHash == null || deviceHash != trustedDeviceHash
    }
}
