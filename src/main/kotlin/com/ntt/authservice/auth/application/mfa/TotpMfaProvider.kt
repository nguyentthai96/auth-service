package com.ntt.authservice.auth.application.mfa

import com.ntt.authservice.auth.application.TotpService
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.exception.MfaCodeInvalidException
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.authservice.shared.exception.TotpNotSetupException
import org.springframework.stereotype.Component

/**
 * TOTP-based MFA provider — verifies time-based one-time passwords.
 * Extracted from MfaService.verifyMfa() when(method) "TOTP" branch.
 *
 * Initiate is no-op for TOTP (client-side app generates codes).
 */
@Component
class TotpMfaProvider(
    private val totpService: TotpService,
    private val userRepository: UserRepository,
    private val auditLogService: AuditLogService
) : MfaProvider {

    override val supportedMethod: String = "TOTP"

    override fun initiate(userId: Long) {
        // TOTP is stateless — no server-side action needed for initiation
    }

    override fun verify(userId: Long, code: String) {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }
        val encryptedSecret = user.totpSecretEncrypted
            ?: throw TotpNotSetupException()
        val secret = totpService.decryptSecret(encryptedSecret)

        if (!totpService.verifyCode(secret, code)) {
            auditLogService.logEvent(
                userId, AuditAction.MFA_VERIFY_FAILED, "User", userId.toString(), "method=TOTP"
            )
            throw MfaCodeInvalidException("Invalid TOTP code")
        }
    }
}
