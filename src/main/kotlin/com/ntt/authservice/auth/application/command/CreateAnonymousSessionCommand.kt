package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.AnonymousSessionResult
import com.ntt.eventsourcingutils.lib.cqrs.command.Command

/**
 * Command to create an anonymous session.
 * Encapsulates IP address for rate limiting and optional device fingerprint.
 */
data class CreateAnonymousSessionCommand(
    val ipAddress: String,
    val deviceFingerprint: String? = null
) : Command<AnonymousSessionResult>
