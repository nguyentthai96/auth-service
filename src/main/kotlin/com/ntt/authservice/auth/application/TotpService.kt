package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP authenticator app service — secret generation, QR URI, verification, and AES-256-GCM encryption.
 */
@Service
class TotpService(
    private val securityProperties: SecurityProperties,
    @Value("\${TOTP_ENCRYPTION_KEY:}") private val encryptionKeyBase64: String
) {

    private val log = LoggerFactory.getLogger(TotpService::class.java)

    private val secretGenerator = DefaultSecretGenerator(32)
    private val codeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val verifier = DefaultCodeVerifier(codeGenerator, SystemTimeProvider()).apply {
        setAllowedTimePeriodDiscrepancy(securityProperties.mfa.totpWindow)
    }

    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    @PostConstruct
    fun validateEncryptionKey() {
        if (encryptionKeyBase64.isBlank()) {
            log.warn("TOTP_ENCRYPTION_KEY is not set — TOTP MFA will not work. Set this env var to enable TOTP.")
            return
        }
        try {
            val keyBytes = java.util.Base64.getDecoder().decode(encryptionKeyBase64)
            require(keyBytes.size == 32) {
                "TOTP_ENCRYPTION_KEY must be 256 bits (32 bytes) in Base64, got ${keyBytes.size} bytes"
            }
            log.info("TOTP encryption key validated successfully (256-bit AES)")
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("TOTP_ENCRYPTION_KEY is invalid: ${e.message}", e)
        }
    }

    /**
     * Generate a new TOTP secret.
     */
    fun generateSecret(): String {
        return secretGenerator.generate()
    }

    /**
     * Build otpauth:// URI for QR code scanning.
     */
    fun generateQrUri(secret: String, username: String, issuer: String): String {
        val data = QrData.Builder()
            .secret(secret)
            .issuer(issuer)
            .label(username)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build()
        return data.uri
    }

    /**
     * Verify TOTP code with ±1 step window.
     */
    fun verifyCode(secret: String, code: String): Boolean {
        return verifier.isValidCode(secret, code)
    }

    /**
     * Encrypt TOTP secret using AES-256-GCM.
     * @return Base64(iv + ciphertext + tag)
     */
    fun encryptSecret(secret: String): String {
        val key = getEncryptionKey()
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(GCM_IV_LENGTH)
        java.security.SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypt TOTP secret from AES-256-GCM.
     */
    fun decryptSecret(encrypted: String): String {
        val key = getEncryptionKey()
        val combined = Base64.getDecoder().decode(encrypted)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getEncryptionKey(): SecretKeySpec {
        if (encryptionKeyBase64.isBlank()) {
            throw com.ntt.authservice.shared.exception.MfaCodeInvalidException(
                "TOTP is not configured — contact your administrator"
            )
        }
        val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
        return SecretKeySpec(keyBytes, "AES")
    }
}
