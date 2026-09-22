package com.ntt.authservice.auth.application.mfa

import com.ntt.authservice.auth.application.LoginResult

/**
 * Strategy interface for MFA providers.
 * FR-011: OCP-compliant — adding a new MFA method = implementing this interface.
 *
 * Each provider handles initiation and verification for its specific method.
 */
interface MfaProvider {

    /** The MFA method this provider supports (e.g., "SMS", "EMAIL", "TOTP"). */
    val supportedMethod: String

    /**
     * Initiate MFA challenge — generate and send code (for OTP providers)
     * or prepare verification (for TOTP).
     *
     * @param userId the user requiring MFA
     */
    fun initiate(userId: Long)

    /**
     * Verify the MFA code provided by the user.
     *
     * @param userId the user verifying MFA
     * @param code the code to verify
     * @throws com.ntt.authservice.shared.exception.MfaCodeInvalidException if code is invalid
     */
    fun verify(userId: Long, code: String)
}
