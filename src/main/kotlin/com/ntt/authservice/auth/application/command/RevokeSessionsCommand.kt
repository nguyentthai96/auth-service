package com.ntt.authservice.auth.application.command

import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Revoke all sessions command — force logout for a user.
 */
data class RevokeSessionsCommand(
    val userId: Long
) : Command<Int>
