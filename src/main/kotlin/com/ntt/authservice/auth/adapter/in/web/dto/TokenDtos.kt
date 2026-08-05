package com.ntt.authservice.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * Token management DTOs — introspection, JWKS, session revocation.
 */
data class IntrospectionRequest(
    @field:NotBlank(message = "Token is required")
    val token: String
)

data class IntrospectionResponse(
    val active: Boolean,
    val sub: String? = null,
    val username: String? = null,
    val roles: List<String>? = null,
    val permissions: List<String>? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val iss: String? = null,
    val jti: String? = null
)

data class RevokeSessionsResponse(
    val revokedCount: Int,
    val userId: Long
)
