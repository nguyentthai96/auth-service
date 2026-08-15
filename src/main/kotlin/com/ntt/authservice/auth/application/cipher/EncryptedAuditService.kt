package com.ntt.authservice.auth.application.cipher

import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithm
import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithmFactory
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.Base64

/**
 * Encrypted audit service — encrypts sensitive payloads before persisting.
 *
 * The main middleware has NO decrypt permission.
 * Decryption requires break-glass procedure via DecryptionVaultService.
 */
@Service
class EncryptedAuditService(
    private val cipherAlgorithmFactory: CipherAlgorithmFactory,
    private val jdbcTemplate: JdbcTemplate
) {

    private val log = LoggerFactory.getLogger(EncryptedAuditService::class.java)

    companion object {
        private const val AUDIT_KEY_ENV = "CIPHER_AUDIT_KEY"
    }

    /**
     * Log an encrypted audit event.
     *
     * @param userId User who performed the action
     * @param action Action type (e.g., "KEY_EXCHANGE", "DECRYPT_REQUEST")
     * @param sensitivePayload Optional payload to encrypt before storage
     * @param keyIdUsed Key session ID involved (if applicable)
     */
    fun logOperation(
        userId: String?,
        action: String,
        sensitivePayload: String? = null,
        keyIdUsed: String? = null
    ) {
        val traceId = MDC.get("traceId") ?: java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val encryptedRef = sensitivePayload?.let { payload ->
            try {
                val auditKey = getAuditKey()
                val aad = "$traceId:$action:$now".toByteArray()
                val encrypted = cipherAlgorithmFactory.encrypt(
                    CipherAlgorithm.AES_GCM,
                    auditKey,
                    payload.toByteArray(),
                    aad
                )
                Base64.getEncoder().encodeToString(encrypted)
            } catch (e: Exception) {
                log.error("Failed to encrypt audit payload: action={}", action, e)
                "[ENCRYPT_FAILED]"
            }
        }

        jdbcTemplate.update(
            """INSERT INTO audit_log_encrypted 
               (trace_id, user_id, action, encrypted_payload_ref, key_id_used, created_at) 
               VALUES (?, ?, ?, ?, ?, ?)""",
            traceId, userId, action, encryptedRef, keyIdUsed, now
        )

        log.info("Encrypted audit logged: traceId={}, action={}, userId={}", traceId, action, userId)
    }

    /**
     * Get audit encryption key from environment.
     * In production, this should come from KMS/Vault.
     */
    private fun getAuditKey(): ByteArray {
        val keyBase64 = System.getenv(AUDIT_KEY_ENV)
            ?: throw IllegalStateException("$AUDIT_KEY_ENV not configured")
        return Base64.getDecoder().decode(keyBase64)
    }
}
