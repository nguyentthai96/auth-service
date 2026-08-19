package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.domain.model.AuthToken

/**
 * Sealed result type for RegisterHandler —
 * replaces ThreadLocal-based promotion result passing with explicit return type.
 * Mirrors LoginResult pattern for consistency.
 */
sealed class RegisterResult {

    data class Success(
        val authToken: AuthToken,
        val promotionResult: PromotionResult? = null
    ) : RegisterResult()
}
