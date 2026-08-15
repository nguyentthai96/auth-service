package com.ntt.authservice.auth.adapter.out.cipher

import com.ntt.basecore.autoconfigure.security.cipher.security.FieldSigningService
import org.springframework.stereotype.Service
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 field signing service.
 *
 * Provides field-level integrity verification for critical endpoints.
 * Thread-safe: creates Mac.getInstance() per call (no instance reuse across threads).
 * Uses constant-time comparison via MessageDigest.isEqual() to prevent timing attacks.
 */
@Service
class HmacFieldSigningServiceImpl : FieldSigningService {

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val FIELD_SEPARATOR = "|"
    }

    override fun computeSignature(
        signingKey: ByteArray,
        fields: Map<String, String>,
        timestamp: String,
        nonce: String,
        keyId: String
    ): ByteArray {
        val signingInput = buildSigningInput(fields, timestamp, nonce, keyId)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey, HMAC_ALGORITHM))
        return mac.doFinal(signingInput.toByteArray())
    }

    override fun verifySignature(
        signingKey: ByteArray,
        fields: Map<String, String>,
        timestamp: String,
        nonce: String,
        keyId: String,
        providedSignature: ByteArray
    ): Boolean {
        val computed = computeSignature(signingKey, fields, timestamp, nonce, keyId)
        // Constant-time comparison — prevents timing attacks
        return MessageDigest.isEqual(computed, providedSignature)
    }

    /**
     * Build signing input from sorted field keys.
     * Format: field1|field2|...|timestamp|nonce|keyId
     */
    private fun buildSigningInput(
        fields: Map<String, String>,
        timestamp: String,
        nonce: String,
        keyId: String
    ): String {
        val sortedFieldValues = fields.toSortedMap().values.joinToString(FIELD_SEPARATOR)
        return "$sortedFieldValues$FIELD_SEPARATOR$timestamp$FIELD_SEPARATOR$nonce$FIELD_SEPARATOR$keyId"
    }
}
