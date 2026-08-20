package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * MFA orchestrator — coordinates OTP, TOTP, trusted device flows.
 */
@Service
class MfaService(
    private val otpService: OtpService,
    private val totpService: TotpService,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val securityProperties: SecurityProperties,
    private val redisTemplate: StringRedisTemplate,
    private val auditLogService: AuditLogService,
    private val rateLimitService: MfaRateLimitService
) {

    private val log = LoggerFactory.getLogger(MfaService::class.java)

    companion object {
        private const val TOTP_SETUP_PREFIX = "mfa:totp:setup:"
        private const val SSO_ONLY_MARKER = "!SSO_ONLY!"
        private const val RECOVERY_CODES_PREFIX = "mfa:recovery:"
        private const val RECOVERY_CODE_COUNT = 10
        private const val RECOVERY_CODE_LENGTH = 8
    }

    /**
     * Initiate MFA challenge — generate OTP or prepare TOTP challenge.
     * @return mfaToken (JWT, 5min TTL) + method
     */
    fun initiateMfa(userId: Long, method: String): LoginResult.MfaRequired {
        when (method) {
            "SMS", "EMAIL" -> {
                val channel = method.lowercase()
                otpService.generateOtp(userId, channel)
                // TODO: dispatch actual SMS/Email via notification service
            }
            "TOTP" -> {
                // TOTP is stateless — no server-side action needed
            }
            else -> throw MfaCodeInvalidException("Unsupported MFA method: $method")
        }

        val mfaToken = jwtService.generateMfaToken(userId, method)
        return LoginResult.MfaRequired(
            mfaToken = mfaToken,
            method = method,
            expiresIn = securityProperties.mfa.mfaTokenTtlSeconds
        )
    }

    /**
     * Verify MFA code (OTP or TOTP) and return full auth response on success.
     * Optionally saves trusted device hash to skip MFA on subsequent logins (FR-005).
     */
    fun verifyMfa(
        mfaToken: String,
        code: String,
        trustDevice: Boolean = false,
        deviceHash: String? = null,
        authResponseBuilder: (Long) -> AuthResponse
    ): AuthResponse {
        val claims = try {
            jwtService.parseMfaToken(mfaToken)
        } catch (e: Exception) {
            throw MfaTokenExpiredException()
        }

        val userId = claims.subject.toLong()
        val method = claims["method"] as? String ?: throw MfaCodeInvalidException("Missing method in MFA token")

        // MFA login rate limit check (FR-003) — applies to all MFA methods
        rateLimitService.checkAndIncrement(userId, RateLimitType.MFA_LOGIN)

        when (method) {
            "SMS", "EMAIL" -> {
                // OTP-specific rate limit check (FR-001)
                rateLimitService.checkAndIncrement(userId, RateLimitType.OTP_VERIFY)
                otpService.verifyOtp(userId, method.lowercase(), code)
            }
            "TOTP" -> {
                val user = userRepository.findById(userId).orElseThrow {
                    ResourceNotFoundException("User", userId)
                }
                val encryptedSecret = user.totpSecretEncrypted
                    ?: throw TotpNotSetupException()
                val secret = totpService.decryptSecret(encryptedSecret)
                if (!totpService.verifyCode(secret, code)) {
                    auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_FAILED, "User", userId.toString(), "method=TOTP")
                    throw MfaCodeInvalidException("Invalid TOTP code")
                }
            }
            else -> throw MfaCodeInvalidException("Unsupported MFA method: $method")
        }

        // Reset rate limit counters on successful verification (FR-011)
        rateLimitService.resetCounters(userId)

        // Save trusted device hash if requested (FR-005)
        if (trustDevice && !deviceHash.isNullOrBlank()) {
            val user = userRepository.findById(userId).orElseThrow {
                ResourceNotFoundException("User", userId)
            }
            user.trustedDeviceHash = deviceHash
            userRepository.save(user)
            log.info("Trusted device set for userId={}", userId)
            auditLogService.logEvent(userId, AuditAction.TRUSTED_DEVICE_SET, "User", userId.toString(), "method=$method")
        }

        log.info("MFA verified for userId={}, method={}", userId, method)
        auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_SUCCESS, "User", userId.toString(), "method=$method")
        return authResponseBuilder(userId)
    }

    /**
     * Setup TOTP — generate secret and store pending in Redis.
     */
    fun setupTotp(userId: Long): TotpSetupResult {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        val secret = totpService.generateSecret()
        val qrUri = totpService.generateQrUri(
            secret = secret,
            username = user.username,
            issuer = securityProperties.jwt.issuer
        )

        // Store pending secret in Redis (10 min TTL)
        redisTemplate.opsForValue().set(
            TOTP_SETUP_PREFIX + userId,
            secret,
            Duration.ofMinutes(10)
        )

        return TotpSetupResult(secret = secret, qrCodeUri = qrUri, issuer = securityProperties.jwt.issuer).also {
            auditLogService.logEvent(userId, AuditAction.MFA_SETUP, "User", userId.toString(), "type=TOTP")
        }
    }

    /**
     * Confirm TOTP setup — verify code against pending secret, then persist.
     */
    fun confirmTotp(userId: Long, code: String): Boolean {
        val pendingSecret = redisTemplate.opsForValue().get(TOTP_SETUP_PREFIX + userId)
            ?: throw TotpNotSetupException()

        if (!totpService.verifyCode(pendingSecret, code)) {
            throw MfaCodeInvalidException("Invalid TOTP confirmation code")
        }

        // Encrypt and persist
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }
        user.totpSecretEncrypted = totpService.encryptSecret(pendingSecret)
        userRepository.save(user)

        // Cleanup Redis
        redisTemplate.delete(TOTP_SETUP_PREFIX + userId)

        log.info("TOTP confirmed for userId={}", userId)
        auditLogService.logEvent(userId, AuditAction.MFA_SETUP, "User", userId.toString(), "type=TOTP, status=confirmed")
        return true
    }

    /**
     * Resend OTP code.
     */
    fun resendOtp(mfaToken: String): LoginResult.MfaRequired {
        val claims = try {
            jwtService.parseMfaToken(mfaToken)
        } catch (e: Exception) {
            throw MfaTokenExpiredException()
        }

        val userId = claims.subject.toLong()
        val method = claims["method"] as? String ?: throw MfaCodeInvalidException("Missing method")

        if (method != "SMS" && method != "EMAIL") {
            throw MfaCodeInvalidException("Resend only available for SMS/EMAIL")
        }

        // Resend count limit: max 3 per MFA session
        val resendKey = "mfa:resend:$userId"
        val resendCount = redisTemplate.opsForValue().increment(resendKey) ?: 1
        if (resendCount == 1L) {
            redisTemplate.expire(resendKey, Duration.ofSeconds(securityProperties.mfa.mfaTokenTtlSeconds))
        }
        if (resendCount > 3) {
            throw MfaMaxAttemptsException("Maximum resend attempts exceeded")
        }

        otpService.generateOtp(userId, method.lowercase())
        // TODO: dispatch actual SMS/Email

        val newMfaToken = jwtService.generateMfaToken(userId, method)
        return LoginResult.MfaRequired(
            mfaToken = newMfaToken,
            method = method,
            expiresIn = securityProperties.mfa.mfaTokenTtlSeconds
        )
    }

    /**
     * Update MFA settings — enable/disable, change method.
     */
    fun updateSettings(userId: Long, enabled: Boolean, method: String?): MfaSettingsResult {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        if (enabled && method == "TOTP" && user.totpSecretEncrypted == null) {
            throw TotpNotSetupException()
        }

        user.mfaEnabled = enabled
        if (method != null) {
            user.mfaMethod = if (enabled) method else "NONE"
        } else if (!enabled) {
            user.mfaMethod = "NONE"
        }
        userRepository.save(user)

        log.info("MFA settings updated for userId={}: enabled={}, method={}", userId, enabled, user.mfaMethod)
        auditLogService.logEvent(userId, AuditAction.MFA_SETUP, "User", userId.toString(), "enabled=$enabled, method=${user.mfaMethod}")
        return MfaSettingsResult(mfaEnabled = user.mfaEnabled, mfaMethod = user.mfaMethod)
    }

    data class TotpSetupResult(val secret: String, val qrCodeUri: String, val issuer: String)
    data class MfaSettingsResult(val mfaEnabled: Boolean, val mfaMethod: String)

    // ===============================
    // Recovery Codes (FR-001)
    // ===============================

    companion object {
        private const val TOTP_SETUP_PREFIX = "mfa:totp:setup:"
        private const val SSO_ONLY_MARKER = "!SSO_ONLY!"
        private const val RECOVERY_CODES_PREFIX = "mfa:recovery:"
        private const val RECOVERY_CODE_COUNT = 10
        private const val RECOVERY_CODE_LENGTH = 8
    }

    /**
     * Generate recovery codes for a user. Called automatically on MFA setup
     * or on-demand via regeneration endpoint (FR-001).
     * Generates 10 single-use codes, stored as BCrypt hashes in Redis.
     */
    fun generateRecoveryCodes(userId: Long): List<String> {
        val user = userRepository.findById(userId).orElseThrow {
            com.ntt.authservice.shared.exception.ResourceNotFoundException("User", userId)
        }

        if (!user.mfaEnabled) {
            throw com.ntt.authservice.shared.exception.MfaCodeInvalidException("MFA must be enabled to generate recovery codes")
        }

        val plainCodes = (1..RECOVERY_CODE_COUNT).map { generateSecureCode() }

        // Store hashed codes in Redis (hash each code for security)
        val hashedCodes = plainCodes.map { hashRecoveryCode(it) }
        val key = RECOVERY_CODES_PREFIX + userId
        redisTemplate.delete(key)
        hashedCodes.forEach { hash ->
            redisTemplate.opsForList().rightPush(key, hash)
        }
        // Recovery codes don't expire — valid until regenerated or MFA disabled

        log.info("Recovery codes generated for userId={}, count={}", userId, RECOVERY_CODE_COUNT)
        auditLogService.logEvent(userId, AuditAction.MFA_SETUP, "User", userId.toString(), "action=recovery_codes_generated")

        return plainCodes
    }

    /**
     * Verify a recovery code — single-use, removes after successful verification.
     * Returns true if code is valid, throws exception otherwise.
     */
    fun verifyRecoveryCode(userId: Long, code: String): Boolean {
        val key = RECOVERY_CODES_PREFIX + userId
        val storedHashes = redisTemplate.opsForList().range(key, 0, -1) ?: emptyList()

        if (storedHashes.isEmpty()) {
            throw com.ntt.authservice.shared.exception.MfaCodeInvalidException("No recovery codes available")
        }

        val matchIndex = storedHashes.indexOfFirst { verifyRecoveryCodeHash(code, it) }
        if (matchIndex == -1) {
            auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_FAILED, "User", userId.toString(), "method=RECOVERY")
            throw com.ntt.authservice.shared.exception.MfaCodeInvalidException("Invalid recovery code")
        }

        // Remove used code (single-use)
        val usedHash = storedHashes[matchIndex]
        redisTemplate.opsForList().remove(key, 1, usedHash)

        log.info("Recovery code used for userId={}, remaining={}", userId, storedHashes.size - 1)
        auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_SUCCESS, "User", userId.toString(), "method=RECOVERY, remaining=${storedHashes.size - 1}")

        return true
    }

    /**
     * Get remaining recovery code count for a user.
     */
    fun getRemainingRecoveryCodeCount(userId: Long): Long {
        val key = RECOVERY_CODES_PREFIX + userId
        return redisTemplate.opsForList().size(key) ?: 0
    }

    private fun generateSecureCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // exclude confusable chars: I, O, 0, 1
        return (1..RECOVERY_CODE_LENGTH)
            .map { chars[java.security.SecureRandom().nextInt(chars.length)] }
            .joinToString("")
            .chunked(4)
            .joinToString("-") // Format: XXXX-XXXX
    }

    private fun hashRecoveryCode(code: String): String {
        val normalized = code.replace("-", "").uppercase()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun verifyRecoveryCodeHash(plainCode: String, storedHash: String): Boolean {
        return hashRecoveryCode(plainCode) == storedHash
    }
}
