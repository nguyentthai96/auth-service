package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.domain.service.TokenHasher
import com.ntt.authservice.auth.adapter.out.persistence.entity.MfaRecoveryCodeEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.MfaRecoveryCodeRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.exception.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Recovery code management — extracted from MfaService for SRP compliance.
 * Handles generation, verification, and counting of single-use MFA recovery codes.
 * Codes are stored as SHA-256 hashes in DB for durability (FR-001).
 */
@Service
class RecoveryCodeService(
    private val recoveryCodeRepository: MfaRecoveryCodeRepository,
    private val userRepository: UserRepository,
    private val auditLogService: AuditLogService
) {

    private val log = LoggerFactory.getLogger(RecoveryCodeService::class.java)

    companion object {
        private const val RECOVERY_CODE_COUNT = 10
        private const val RECOVERY_CODE_LENGTH = 8
    }

    /**
     * Generate recovery codes for a user. Called automatically on MFA setup
     * or on-demand via regeneration endpoint (FR-001).
     * Generates 10 single-use codes, stored as SHA-256 hashes in DB.
     */
    @Transactional
    fun generateRecoveryCodes(userId: Long): List<String> {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        if (!user.mfaEnabled) {
            throw MfaCodeInvalidException("MFA must be enabled to generate recovery codes")
        }

        // Delete existing codes before regeneration
        recoveryCodeRepository.deleteByUserId(userId)

        val plainCodes = (1..RECOVERY_CODE_COUNT).map { generateSecureCode() }

        // Persist hashed codes in DB for durability
        val entities = plainCodes.map { code ->
            MfaRecoveryCodeEntity().apply {
                this.userId = userId
                this.codeHash = hashRecoveryCode(code)
                this.used = false
            }
        }
        recoveryCodeRepository.saveAll(entities)

        log.info("Recovery codes generated for userId={}, count={}", userId, RECOVERY_CODE_COUNT)
        auditLogService.logEvent(userId, AuditAction.MFA_SETUP, "User", userId.toString(), "action=recovery_codes_generated")

        return plainCodes
    }

    /**
     * Verify a recovery code — single-use, marks as used after successful verification.
     * Returns true if code is valid, throws exception otherwise.
     */
    @Transactional
    fun verifyRecoveryCode(userId: Long, code: String): Boolean {
        val unusedCodes = recoveryCodeRepository.findByUserIdAndUsedFalse(userId)

        if (unusedCodes.isEmpty()) {
            throw MfaCodeInvalidException("No recovery codes available")
        }

        val hashedInput = hashRecoveryCode(code)
        val matchedCode = unusedCodes.find { it.codeHash == hashedInput }

        if (matchedCode == null) {
            auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_FAILED, "User", userId.toString(), "method=RECOVERY")
            throw MfaCodeInvalidException("Invalid recovery code")
        }

        // Mark code as used (single-use)
        matchedCode.used = true
        matchedCode.usedAt = Instant.now()
        recoveryCodeRepository.save(matchedCode)

        val remaining = unusedCodes.size - 1
        log.info("Recovery code used for userId={}, remaining={}", userId, remaining)
        auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_SUCCESS, "User", userId.toString(), "method=RECOVERY, remaining=$remaining")

        return true
    }

    /**
     * Get remaining recovery code count for a user.
     */
    fun getRemainingRecoveryCodeCount(userId: Long): Long {
        return recoveryCodeRepository.countByUserIdAndUsedFalse(userId)
    }

    private fun generateSecureCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // exclude confusable chars: I, O, 0, 1
        val random = java.security.SecureRandom()
        return (1..RECOVERY_CODE_LENGTH)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
            .chunked(4)
            .joinToString("-") // Format: XXXX-XXXX
    }

    private fun hashRecoveryCode(code: String): String {
        val normalized = code.replace("-", "").uppercase()
        return TokenHasher.hashHex(normalized)
    }
}
