package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.TokenStore
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Revoke sessions handler — force logout by revoking all refresh tokens.
 */
@Component
class RevokeSessionsHandler(
    private val tokenStore: TokenStore,
    private val userPort: UserPort,
    private val auditLogService: AuditLogService
) : CommandHandler<RevokeSessionsCommand, Int> {

    private val log = LoggerFactory.getLogger(RevokeSessionsHandler::class.java)

    override fun commandType(): Class<RevokeSessionsCommand> = RevokeSessionsCommand::class.java

    @Transactional
    override fun handle(command: RevokeSessionsCommand): Int {
        // Verify user exists
        userPort.findById(command.userId)
            ?: throw ResourceNotFoundException("User", command.userId)

        val revokedCount = tokenStore.revokeAllForUser(command.userId)
        log.info("All sessions revoked for userId={}, count={}", command.userId, revokedCount)
        auditLogService.logEvent(command.userId, AuditAction.FORCE_LOGOUT, "User", command.userId.toString(), "revokedTokens=$revokedCount")
        return revokedCount
    }


}
