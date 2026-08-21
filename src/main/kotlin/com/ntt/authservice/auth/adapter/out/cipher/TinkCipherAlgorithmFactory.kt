package com.ntt.authservice.auth.adapter.out.cipher

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.ntt.authservice.shared.exception.CipherDecryptFailedException
import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithm
import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithmFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.GeneralSecurityException

/**
 * Tink-based AEAD implementation — overrides DefaultCipherAlgorithmFactory (base-core)
 * via @ConditionalOnMissingBean in CipherCoreConfiguration.
 *
 * Thread-safe: Tink Aead instances are thread-safe after creation.
 * Nonce: Tink generates unique random nonces automatically for each encrypt() call.
 */
@Service
class TinkCipherAlgorithmFactory : CipherAlgorithmFactory {

    private val log = LoggerFactory.getLogger(TinkCipherAlgorithmFactory::class.java)

    init {
        AeadConfig.register()
        log.info("Tink AEAD config registered — available algorithms: AES_GCM, CHACHA20_POLY1305")
    }

    override fun encrypt(
        algorithm: CipherAlgorithm,
        keyBytes: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        val aead = createAead(algorithm, keyBytes)
        return try {
            aead.encrypt(plaintext, associatedData)
        } catch (e: GeneralSecurityException) {
            log.error("Tink encrypt failed: algorithm={}", algorithm, e)
            throw CipherDecryptFailedException("Encryption failed: ${e.message}")
        }
    }

    override fun decrypt(
        algorithm: CipherAlgorithm,
        keyBytes: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        val aead = createAead(algorithm, keyBytes)
        return try {
            aead.decrypt(ciphertext, associatedData)
        } catch (e: GeneralSecurityException) {
            log.warn("Tink decrypt failed: algorithm={}, likely tampered payload", algorithm)
            throw CipherDecryptFailedException("Decryption failed — corrupted or tampered payload")
        }
    }

    /**
     * Create Tink AEAD primitive from raw key bytes.
     *
     * Uses Tink's KeysetHandle to register the raw key with appropriate parameters.
     * For AES-GCM: requires 32 bytes (AES-256-GCM).
     * For ChaCha20: requires 32 bytes (ChaCha20-Poly1305).
     */
    private fun createAead(algorithm: CipherAlgorithm, keyBytes: ByteArray): Aead {
        return when (algorithm) {
            CipherAlgorithm.AES_GCM -> com.google.crypto.tink.subtle.AesGcmJce(keyBytes)
            CipherAlgorithm.CHACHA20_POLY1305 -> com.google.crypto.tink.subtle.ChaCha20Poly1305(keyBytes)
        }
    }
}
