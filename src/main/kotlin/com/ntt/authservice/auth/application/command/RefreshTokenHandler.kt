package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.TokenStore
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.authservice.shared.exception.TokenExpiredException
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Refresh token handler — extracted from AuthService.refreshToken().
 * Implements token rotation with revocation.
 */
@Component
class RefreshTokenHandler(
    private val tokenStore: TokenStore,
    private val userPort: UserPort,
    private val tokenGenerator: TokenGenerator
) : CommandHandler<RefreshTokenCommand, AuthToken> {

    @Transactional
    override suspend fun handle(command: RefreshTokenCommand): AuthToken {
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

        val domainCode = tokenGenerator.getPrimaryDomain(user.id.value)
        return tokenGenerator.generateAuthResponse(user, domainCode)
    }


}
