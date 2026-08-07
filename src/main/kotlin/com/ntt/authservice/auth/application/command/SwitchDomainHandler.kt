package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.DomainPort
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.shared.exception.PermissionDeniedException
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Switch domain handler — extracted from AuthService.switchDomain().
 */
@Component
class SwitchDomainHandler(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val tokenGenerator: TokenGenerator
) : CommandHandler<SwitchDomainCommand, AuthToken> {

    @Transactional(readOnly = true)
    override suspend fun handle(command: SwitchDomainCommand): AuthToken {
        val user = userPort.findById(command.userId)
            ?: throw ResourceNotFoundException("User", command.userId)

        val domain = domainPort.findByCodeAndActive(command.newDomainCode)
            ?: throw ResourceNotFoundException("Domain", command.newDomainCode)

        // TODO: Verify user is member of target domain via UserDomainPort
        // For now, delegate to tokenGenerator
        return tokenGenerator.generateAuthResponse(user, command.newDomainCode)
    }


}
