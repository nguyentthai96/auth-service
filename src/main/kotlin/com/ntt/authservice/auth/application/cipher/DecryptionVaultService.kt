package com.ntt.authservice.auth.application.cipher

import com.ntt.authservice.auth.adapter.out.persistence.entity.VaultAccessLogEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.VaultAccessLogJpaRepository
import com.ntt.authservice.shared.exception.CipherContextMismatchException
import com.ntt.authservice.shared.exception.CipherKeyExpiredException
import com.ntt.authservice.shared.exception.PermissionDeniedException
import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithm
import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithmFactory
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Break-glass decryption vault service.
 *
 * 4-eyes principle:
 * 1. Requester creates access request
 * 2. Approver (different person) approves
 * 3. JIT access token created (30min TTL, max 10 decrypts)
 * 4. Requester uses token to decrypt specific audit logs
 *
 * All operations are audit-logged.
 */
@Service
class DecryptionVaultService(
    private val vaultRepository: VaultAccessLogJpaRepository,
    private val cipherAlgorithmFactory: CipherAlgorithmFactory,
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(DecryptionVaultService::class.java)

    companion object {
        private const val VAULT_TOKEN_PREFIX = "vault:token:"
        private const val VAULT_TOKEN_TTL_MINUTES = 30L
        private const val MAX_DECRYPTS_PER_SESSION = 10
    }

    /**
     * Create a pending access request (step 1 of 4-eyes).
     */
    @Transactional
    fun requestAccess(requesterId: String, targetAuditId: Long): Long {
        val entity = VaultAccessLogEntity(
            requesterId = requesterId,
            requestType = "DECRYPT",
            status = "PENDING",
            targetAuditId = targetAuditId,
            maxDecrypts = MAX_DECRYPTS_PER_SESSION,
            createdAt = System.currentTimeMillis()
        )
        val saved = vaultRepository.save(entity)
        log.info("Vault access requested: id={}, requester={}, target={}", saved.id, requesterId, targetAuditId)
        return saved.id!!
    }

    /**
     * Approve an access request (step 2 of 4-eyes).
     * Approver must be different from requester.
     */
    @Transactional
    fun approve(approverId: String, requestId: Long): String {
        val request = vaultRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("Vault request not found: $requestId") }

        if (request.requesterId == approverId) {
            throw PermissionDeniedException("Requester cannot approve their own request (4-eyes principle)")
        }

        if (request.status != "PENDING") {
            throw CipherContextMismatchException("Request is not pending: status=${request.status}")
        }

        val now = System.currentTimeMillis()
        val token = UUID.randomUUID().toString()

        request.approverId = approverId
        request.status = "APPROVED"
        request.approvedAt = now
        request.expiresAt = now + (VAULT_TOKEN_TTL_MINUTES * 60 * 1000)
        vaultRepository.save(request)

        // Store JIT token in Redis
        redisTemplate.opsForValue().set(
            "$VAULT_TOKEN_PREFIX$token",
            requestId.toString(),
            VAULT_TOKEN_TTL_MINUTES,
            TimeUnit.MINUTES
        )

        log.info("Vault access approved: id={}, approver={}", requestId, approverId)
        return token
    }

    /**
     * Decrypt a specific audit log entry using a valid vault token.
     */
    @Transactional
    fun decrypt(accessToken: String, auditLogId: Long): String {
        // Validate token
        val requestIdStr = redisTemplate.opsForValue().get("$VAULT_TOKEN_PREFIX$accessToken")
            ?: throw CipherKeyExpiredException("vault-token")

        val requestId = requestIdStr.toLong()
        val request = vaultRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("Vault request not found") }

        if (request.status != "APPROVED") {
            throw PermissionDeniedException("Vault access not approved")
        }

        if (request.decryptCount >= request.maxDecrypts) {
            throw PermissionDeniedException("Maximum decrypts (${request.maxDecrypts}) exceeded")
        }

        if (request.expiresAt != null && System.currentTimeMillis() > request.expiresAt!!) {
            throw CipherKeyExpiredException("vault-request-$requestId")
        }

        // Fetch encrypted payload
        val row = jdbcTemplate.queryForMap(
            "SELECT encrypted_payload_ref, trace_id, action, created_at FROM audit_log_encrypted WHERE id = ?",
            auditLogId
        )

        val encryptedRef = row["encrypted_payload_ref"] as? String
            ?: throw IllegalArgumentException("No encrypted payload for audit log: $auditLogId")

        // Decrypt
        val auditKey = Base64.getDecoder().decode(
            System.getenv("CIPHER_AUDIT_KEY")
                ?: throw IllegalStateException("CIPHER_AUDIT_KEY not configured")
        )
        val traceId = row["trace_id"] as String
        val action = row["action"] as String
        val createdAt = row["created_at"] as Long
        val aad = "$traceId:$action:$createdAt".toByteArray()

        val decrypted = cipherAlgorithmFactory.decrypt(
            CipherAlgorithm.AES_GCM,
            auditKey,
            Base64.getDecoder().decode(encryptedRef),
            aad
        )

        // Increment decrypt count
        request.decryptCount += 1
        vaultRepository.save(request)

        log.info("Vault decrypt: requestId={}, auditLogId={}, decryptCount={}/{}",
            requestId, auditLogId, request.decryptCount, request.maxDecrypts)

        return String(decrypted)
    }

    /**
     * Revoke a vault access request.
     */
    @Transactional
    fun revokeAccess(requestId: Long) {
        val request = vaultRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("Vault request not found: $requestId") }
        request.status = "REVOKED"
        vaultRepository.save(request)
        log.info("Vault access revoked: id={}", requestId)
    }
}
