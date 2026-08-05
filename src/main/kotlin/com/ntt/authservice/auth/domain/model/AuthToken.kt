package com.ntt.authservice.auth.domain.model

import java.time.Instant

/**
 * Auth token domain model — immutable.
 * Represents the response tokens issued after successful authentication.
 */
data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val userId: Long,
    val username: String,
    val activeDomain: String,
    val roles: List<String>,
    val permissions: List<String>
)
