package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Register command — CQRS write-side for user registration.
 */
data class RegisterCommand(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
    val domainCode: String
) : Command<AuthToken>
