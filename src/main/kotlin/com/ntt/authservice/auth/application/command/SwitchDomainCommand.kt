package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Switch domain command — changes active domain without re-authentication.
 */
data class SwitchDomainCommand(
    val userId: Long,
    val newDomainCode: String
) : Command<AuthToken>
