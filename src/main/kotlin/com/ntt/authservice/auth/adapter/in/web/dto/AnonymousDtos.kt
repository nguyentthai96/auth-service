package com.ntt.authservice.auth.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * Request/Response DTOs for anonymous session endpoints.
 */

data class CreateAnonymousSessionRequest(
    val deviceFingerprint: String? = null
)

data class StoreSessionDataRequest(
    @field:NotBlank val namespace: String,
    @field:NotBlank val key: String,
    @field:NotNull val value: Any
)

data class AnonymousTokenResponse(
    val token: String,
    val sessionId: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

data class SessionDataResponse(
    val namespace: String,
    val key: String,
    val value: Any?
)

data class DataTransferredInfo(
    val itemCount: Int,
    val namespaces: List<String>,
    val status: String
)
