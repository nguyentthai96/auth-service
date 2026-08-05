package com.ntt.authservice.auth.application.query

import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to build AuthResponse for a user (used by MFA verify and SSO callback).
 */
data class BuildAuthResponseQuery(
    val userId: Long
) : Query<AuthToken>
