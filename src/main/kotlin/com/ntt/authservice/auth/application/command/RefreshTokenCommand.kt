package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Refresh token command — rotates the refresh token and issues new access/refresh pair.
 */
data class RefreshTokenCommand(
    val refreshToken: String
) : Command<AuthToken>
