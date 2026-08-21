package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.RegisterResult
import com.ntt.authservice.auth.application.SessionPromotionService
import com.ntt.authservice.auth.application.event.EventService
import com.ntt.authservice.auth.application.port.out.*
import com.ntt.authservice.auth.domain.event.UserRegisteredEvent
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
 * Supports anonymous session promotion on registration (DD-006).
 *
 * Returns RegisterResult sealed class (DD-013) — replaces ThreadLocal-based
 * promotion result passing for thread-safety and explicit data flow.
 *
 * FR-001: Enriched event payload
 * FR-004: Event store persistence via EventService
 * FR-007: Transactional outbox via EventService
 * FR-020: Structured transaction logging
 */
@Component
class RegisterHandler(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val eventPublisher: EventPublisher,
    private val eventService: EventService,
    private val tokenGenerator: TokenGenerator,
    private val sessionPromotionService: SessionPromotionService
) : CommandHandler<RegisterCommand, RegisterResult> {

    private val log = LoggerFactory.getLogger(RegisterHandler::class.java)

    override fun commandType(): Class<RegisterCommand> = RegisterCommand::class.java

    @Transactional
    override fun handle(command: RegisterCommand): RegisterResult {
        log.debug("REGISTER_START username={}, domain={}, correlationId={}",
            command.username, command.domainCode, command.correlationId)

        // Validate uniqueness
        if (userPort.existsByUsername(command.username)) {
            throw DuplicateResourceException("User", "username", command.username)
        }
        if (userPort.existsByEmail(command.email)) {
            throw DuplicateResourceException("User", "email", command.email)
        }
        log.debug("REGISTER_VALIDATION_PASSED username={}", command.username)

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
        log.debug("REGISTER_USER_PERSISTED userId={}, username={}", savedUser.id.value, savedUser.username)

        // Record enriched domain event via EventService (transactional: event_store + outbox)
        eventService.record(
            aggregateType = "User",
            aggregateId = savedUser.id.value,
            event = UserRegisteredEvent(
                userId = savedUser.id.value,
                username = savedUser.username,
                email = command.email,
                fullName = command.fullName,
                phone = command.phone,
                domainCode = command.domainCode,
                domainId = domain.id,
                status = "ACTIVE",
                registrationSource = determineRegistrationSource(command),
                ipAddress = command.ipAddress,
                userAgent = command.userAgent
            ),
            topic = "iam.user.registered",
            partitionKey = savedUser.id.value.toString(),
            correlationId = command.correlationId
        )
        log.debug("REGISTER_EVENT_RECORDED userId={}, topic=iam.user.registered", savedUser.id.value)

        log.info("User registered: {} in domain: {}", savedUser.username, command.domainCode)

        // Generate auth tokens
        val authToken = tokenGenerator.generateAuthResponse(savedUser, command.domainCode)
        log.debug("REGISTER_TOKEN_GENERATED userId={}", savedUser.id.value)

        // Anonymous session promotion (best-effort — DD-006, DD-007)
        val promotionResult = if (!command.anonymousSessionId.isNullOrBlank()) {
            try {
                sessionPromotionService.promoteSession(
                    sessionId = command.anonymousSessionId,
                    userId = savedUser.id.value,
                    anonymousJti = command.anonymousTokenJti ?: ""
                )
            } catch (e: Exception) {
                log.warn("Anonymous session promotion failed during register for session {}: {}",
                    command.anonymousSessionId, e.message)
                PromotionResult(PromotionResult.Status.FAILED)
            }
        } else null

        log.debug("REGISTER_COMPLETE userId={}, promoted={}", savedUser.id.value,
            promotionResult?.status?.name ?: "N/A")

        return RegisterResult.Success(authToken, promotionResult)
    }

    /**
     * Determine registration source based on command context.
     * "ANONYMOUS_PROMOTION" if anonymous session promotion is requested,
     * "DIRECT" otherwise.
     */
    private fun determineRegistrationSource(command: RegisterCommand): String {
        return if (!command.anonymousSessionId.isNullOrBlank()) {
            "ANONYMOUS_PROMOTION"
        } else {
            "DIRECT"
        }
    }
}
