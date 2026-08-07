package com.ntt.authservice.auth.application.query

import com.ntt.authservice.auth.application.command.TokenGenerator
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * BuildAuthResponseHandler — replaces AuthService.buildAuthResponseForUser().
 * Used by MFA verify and SSO callback flows.
 */
@Component
class BuildAuthResponseHandler(
    private val userPort: UserPort,
    private val tokenGenerator: TokenGenerator
) : QueryHandler<BuildAuthResponseQuery, AuthToken> {

    @Transactional(readOnly = true)
    override suspend fun handle(query: BuildAuthResponseQuery): AuthToken {
        val user = userPort.findById(query.userId)
            ?: throw ResourceNotFoundException("User", query.userId)

        val domainCode = tokenGenerator.getPrimaryDomain(user.id.value)
        return tokenGenerator.generateAuthResponse(user, domainCode)
    }


}
