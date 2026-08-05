package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse

/**
 * Sealed result type for AuthService.login() —
 * distinguishes full authentication from MFA-pending state.
 */
sealed class LoginResult {

    data class Success(val response: AuthResponse) : LoginResult()

    data class MfaRequired(
        val mfaToken: String,
        val method: String,
        val expiresIn: Long
    ) : LoginResult()
}
