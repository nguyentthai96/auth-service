package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.model.AuthToken
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.shared.exception.DuplicateResourceException
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Register handler — extracted from AuthService.register().
 */
@Component
class RegisterHandler(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val eventPublisher: EventPublisher,
    private val tokenGenerator: TokenGenerator
) : CommandHandler<RegisterCommand, AuthToken> {

    private val log = LoggerFactory.getLogger(RegisterHandler::class.java)

    override fun commandType(): Class<RegisterCommand> = RegisterCommand::class.java

    @Transactional
    override fun handle(command: RegisterCommand): AuthToken {
        // Validate uniqueness
        if (userPort.existsByUsername(command.username)) {
            throw DuplicateResourceException("User", "username", command.username)
        }
        if (userPort.existsByEmail(command.email)) {
            throw DuplicateResourceException("User", "email", command.email)
        }

        // Validate domain exists
        val domain = domainPort.findByCodeAndActive(command.domainCode)
            ?: throw ResourceNotFoundException("Domain", command.domainCode)

        // Create user via domain model
        val encodedPassword = tokenGenerator.encodePassword(command.password)
        val user = User(
            id = UserId(0), // will be assigned by persistence layer
            username = command.username,
            email = Email(command.email),
            passwordHash = PasswordHash(encodedPassword),
            fullName = command.fullName,
            phone = command.phone,
            status = UserStatus.Active
        )

        val savedUser = userPort.save(user)

        // Publish domain event
        eventPublisher.publish(
            UserRegisteredEvent(
                userId = savedUser.id.value,
                username = savedUser.username,
                domainCode = command.domainCode
            )
        )

        log.info("User registered: {} in domain: {}", savedUser.username, command.domainCode)

        return tokenGenerator.generateAuthResponse(savedUser, command.domainCode)
    }


}
