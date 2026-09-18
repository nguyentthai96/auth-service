package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.AccountLockoutService
import com.ntt.eventsourcingutils.lib.cqrs.command.Command
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Command: Self-service unlock via email link token (FR-013).
 */
data class SelfServiceUnlockCommand(
    val token: String
) : Command<SelfServiceUnlockResult>

/**
 * Result of self-service unlock operation.
 */
sealed interface SelfServiceUnlockResult {
    data class Success(val userId: Long) : SelfServiceUnlockResult
    data object TokenExpired : SelfServiceUnlockResult
    data object TokenUsedOrInvalid : SelfServiceUnlockResult
    data object NotAllowed : SelfServiceUnlockResult
}

/**
 * Handler: Self-service unlock via email link.
 * Delegates to AccountLockoutService.processSelfServiceUnlock().
 */
@Component
class SelfServiceUnlockHandler(
    private val accountLockoutService: AccountLockoutService
) : CommandHandler<SelfServiceUnlockCommand, SelfServiceUnlockResult> {

    private val log = LoggerFactory.getLogger(SelfServiceUnlockHandler::class.java)

    override fun commandType(): Class<SelfServiceUnlockCommand> = SelfServiceUnlockCommand::class.java

    @Transactional
    override fun handle(command: SelfServiceUnlockCommand): SelfServiceUnlockResult {
        val userId = accountLockoutService.processSelfServiceUnlock(command.token)
        return if (userId != null) {
            log.info("SELF_UNLOCK_HANDLED userId={}", userId)
            SelfServiceUnlockResult.Success(userId)
        } else {
            log.warn("SELF_UNLOCK_FAILED token={}", command.token.take(8) + "***")
            SelfServiceUnlockResult.TokenUsedOrInvalid
        }
    }
}
