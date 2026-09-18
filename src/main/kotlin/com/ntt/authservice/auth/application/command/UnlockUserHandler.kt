package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.AccountLockoutService
import com.ntt.eventsourcingutils.lib.cqrs.command.Command
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Command: Admin unlock a locked user account (FR-009).
 */
data class UnlockUserCommand(
    val userId: Long,
    val adminId: Long
) : Command<Boolean>

/**
 * Handler: Admin unlock a locked user account.
 * Delegates to AccountLockoutService.processAdminUnlock().
 */
@Component
class UnlockUserHandler(
    private val accountLockoutService: AccountLockoutService
) : CommandHandler<UnlockUserCommand, Boolean> {

    private val log = LoggerFactory.getLogger(UnlockUserHandler::class.java)

    override fun commandType(): Class<UnlockUserCommand> = UnlockUserCommand::class.java

    @Transactional
    override fun handle(command: UnlockUserCommand): Boolean {
        accountLockoutService.processAdminUnlock(command.userId, command.adminId)
        log.info("ADMIN_UNLOCK_HANDLED userId={} by adminId={}", command.userId, command.adminId)
        return true
    }
}
