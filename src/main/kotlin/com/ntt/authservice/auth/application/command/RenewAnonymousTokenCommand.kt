package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.AnonymousSessionResult
import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Command to renew an anonymous token.
 * Requires the current (valid) anonymous token.
 */
data class RenewAnonymousTokenCommand(
    val currentToken: String
) : Command<AnonymousSessionResult>
