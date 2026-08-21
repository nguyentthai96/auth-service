package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.event.TokenEventRecorder
import com.ntt.authservice.auth.application.port.out.TokenStore
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.event.RevocationType
import com.ntt.authservice.auth.domain.event.IssuanceContext
import com.ntt.authservice.auth.domain.event.TokenRevokedEvent
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.TokenIssuanceMetadata
import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.authservice.shared.exception.TokenExpiredException
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Refresh token handler — extracted from AuthService.refreshToken().
 * Implements token rotation with revocation.
 *
 * FR-009: Records TokenRevokedEvent(ROTATION) after old token revocation,
 * then delegates to TokenGenerator which records TokenIssuedEvent(TOKEN_REFRESH).
 * Both events share a correlationId for rotation chain tracing.
 */
@Component
class RefreshTokenHandler(
    private val tokenStore: TokenStore,
    private val userPort: UserPort,
    private val tokenGenerator: TokenGenerator,
    private val tokenEventRecorder: TokenEventRecorder
) : CommandHandler<RefreshTokenCommand, AuthToken> {

    override fun commandType(): Class<RefreshTokenCommand> = RefreshTokenCommand::class.java

    @Transactional
    override fun handle(command: RefreshTokenCommand): AuthToken {
        val tokenHash = TokenHasher.hash(command.refreshToken)
        val storedToken = tokenStore.findValidRefreshToken(tokenHash)
            ?: throw TokenExpiredException()

        if (storedToken.expiresAt.isBefore(Instant.now())) {
            tokenStore.revokeToken(tokenHash)
            throw TokenExpiredException()
        }

        val user = userPort.findById(storedToken.userId)
            ?: throw ResourceNotFoundException("User", storedToken.userId)

        // Revoke old refresh token (rotation)
        tokenStore.revokeToken(tokenHash)

        // Record revocation event (FR-009)
        val correlationId = UUID.randomUUID().toString()
        tokenEventRecorder.recordRevocation(
            event = TokenRevokedEvent(
                userId = user.id.value,
                revocationType = RevocationType.ROTATION,
                revokedTokenHash = tokenHash,
                reason = "Token rotation during refresh"
            ),
            userId = user.id.value,
            correlationId = correlationId
        )

        val domainCode = tokenGenerator.getPrimaryDomain(user.id.value)

        // Pass metadata with TOKEN_REFRESH context + shared correlationId
        val metadata = TokenIssuanceMetadata(
            issuanceContext = IssuanceContext.TOKEN_REFRESH,
            previousRefreshTokenHash = tokenHash,
            correlationId = correlationId
        )
        return tokenGenerator.generateAuthResponse(user, domainCode, metadata)
    }
}
