package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Login command — CQRS write-side for user authentication.
 * Encapsulates all data needed to authenticate a user.
 */
data class LoginCommand(
    val username: String,
    val password: String,
    val domainCode: String? = null,
    val captchaToken: String? = null,
    val trustedDeviceHash: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val deviceFingerprint: String? = null,
    val anonymousSessionId: String? = null
) : Command<LoginResult>

