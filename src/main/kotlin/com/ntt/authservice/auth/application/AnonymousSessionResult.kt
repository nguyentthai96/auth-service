package com.ntt.authservice.auth.application

/**
 * Result of creating or renewing an anonymous session.
 * Returned by AnonymousSessionHandler and RenewAnonymousTokenHandler.
 */
data class AnonymousSessionResult(
    val token: String,
    val sessionId: String,
    val expiresIn: Long
)
