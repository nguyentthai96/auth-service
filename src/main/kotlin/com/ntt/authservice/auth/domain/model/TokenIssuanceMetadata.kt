package com.ntt.authservice.auth.domain.model

import com.ntt.authservice.auth.domain.event.IssuanceContext

/**
 * Contextual metadata for token issuance — passed to TokenGenerator.generateAuthResponse()
 * to enrich TokenIssuedEvent with caller context (IP, user-agent, correlationId).
 *
 * Optional parameter with backward compatibility — callers without metadata pass null.
 */
data class TokenIssuanceMetadata(
    val issuanceContext: IssuanceContext,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val correlationId: String? = null,
    val previousRefreshTokenHash: String? = null,
    val deviceFingerprint: String? = null
)
