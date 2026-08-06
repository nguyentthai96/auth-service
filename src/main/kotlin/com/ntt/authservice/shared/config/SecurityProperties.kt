package com.ntt.authservice.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Centralized security configuration properties.
 * Maps to `app.security.*` in application.yml.
 */
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val enabled: Boolean = true,
    val jwt: JwtProperties = JwtProperties(),
    val password: PasswordProperties = PasswordProperties(),
    val mfa: MfaProperties = MfaProperties(),
    val captcha: CaptchaProperties = CaptchaProperties(),
    val sso: SsoProperties = SsoProperties()
) {
    data class JwtProperties(
        val secretKey: String = "",
        val algorithm: String = "RS256",
        val privateKeyPath: String = "",
        val publicKeyPath: String = "",
        val keyId: String = "auth-service-key-1",
        val accessTokenExpirationMs: Long = 1_800_000,   // 30 min
        val refreshTokenExpirationMs: Long = 604_800_000, // 7 days
        val issuer: String = "auth-service"
    )

    data class PasswordProperties(
        val bcryptStrength: Int = 12,
        val maxFailedAttempts: Int = 3,
        val lockDurationMinutes: Int = 15
    )

    data class MfaProperties(
        val otpTtlSeconds: Long = 300,
        val maxAttempts: Int = 3,
        val totpWindow: Int = 1,
        val mfaTokenTtlSeconds: Long = 300,
        val trustedDeviceTtlDays: Long = 30,
        val rateLimit: RateLimitProperties = RateLimitProperties()
    ) {
        data class RateLimitProperties(
            val otp: LimitConfig = LimitConfig(maxAttempts = 5, windowSeconds = 900, lockSeconds = 1800),
            val login: LimitConfig = LimitConfig(maxAttempts = 10, windowSeconds = 3600, lockSeconds = 3600),
            val redisTimeoutMs: Long = 500
        )

        data class LimitConfig(
            val maxAttempts: Int,
            val windowSeconds: Long,
            val lockSeconds: Long
        )
    }

    data class CaptchaProperties(
        val provider: String = "noop",
        val secretKey: String = "",
        val siteKey: String = "",
        val verifyUrl: String = ""
    )

    data class SsoProperties(
        val enabled: Boolean = false,
        val autoProvisionEnabled: Boolean = false,
        val defaultDomainCode: String = "default",
        val timeoutMs: Long = 10_000
    )
}
