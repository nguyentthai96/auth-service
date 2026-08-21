package com.ntt.authservice.auth.adapter.`in`.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

/**
 * Token management DTOs — introspection (RFC 7662), JWKS, session revocation.
 */
data class IntrospectionRequest(
    @field:NotBlank(message = "Token is required")
    val token: String
)

/**
 * RFC 7662 Token Introspection Response.
 * Extends base fields with token_type, scope, client_id for spec compliance.
 */
data class IntrospectionResponse(
    val active: Boolean,
    val sub: String? = null,
    val username: String? = null,
    val roles: List<String>? = null,
    val permissions: List<String>? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val iss: String? = null,
    val jti: String? = null,
    /** RFC 7662: Token type (e.g., "Bearer"). */
    @JsonProperty("token_type")
    val tokenType: String? = null,
    /** RFC 7662: Space-separated scope string. */
    val scope: String? = null,
    /** RFC 7662: Client identifier (from aud claim). */
    @JsonProperty("client_id")
    val clientId: String? = null
)

data class RevokeSessionsResponse(
    val revokedCount: Int,
    val userId: Long
)
