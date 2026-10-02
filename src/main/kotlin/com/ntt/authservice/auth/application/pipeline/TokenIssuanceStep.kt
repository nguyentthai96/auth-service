package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.application.command.TokenGenerator
import com.ntt.authservice.auth.domain.event.IssuanceContext
import com.ntt.authservice.auth.domain.model.TokenIssuanceMetadata
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Step 5: Token issuance — generate JWT access/refresh tokens.
 * FR-008: Generate authentication tokens with device fingerprint claim.
 * Domain logic removed — global RBAC scope.
 */
@Component
class TokenIssuanceStep(
    private val tokenGenerator: TokenGenerator
) : AuthenticationStep {

    private val log = LoggerFactory.getLogger(TokenIssuanceStep::class.java)

    override val order: Int = 500
    override val name: String = "TokenIssuance"

    override fun execute(context: AuthenticationContext): StepOutcome {
        val command = context.command
        val user = context.user
            ?: throw IllegalStateException("User not resolved in previous step")

        // Resolve fingerprint
        val resolvedFingerprint = command.deviceFingerprint

        // Generate tokens with fingerprint claim (global scope)
        val metadata = TokenIssuanceMetadata(
            issuanceContext = IssuanceContext.LOGIN,
            ipAddress = command.ipAddress,
            userAgent = command.userAgent,
            deviceFingerprint = resolvedFingerprint
        )
        val authToken = tokenGenerator.generateAuthResponse(user, metadata)

        log.debug("Token issued for user: {}", user.username)
        return StepOutcome.Continue(
            context.copy(authResponse = AuthResponse.from(authToken))
        )
    }
}
