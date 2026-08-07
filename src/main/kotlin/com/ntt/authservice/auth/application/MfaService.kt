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
     */
    fun verifyMfa(mfaToken: String, code: String, authResponseBuilder: (Long) -> AuthResponse): AuthResponse {
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
}
