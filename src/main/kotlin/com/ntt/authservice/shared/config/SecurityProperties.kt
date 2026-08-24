package com.ntt.authservice.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Centralized security configuration properties.
 * Maps to `app.security.*` in application.yml.
 *
 * **Key Defaults:**
 * - CAPTCHA threshold: Triggered after `password.maxFailedAttempts` (default: 3) consecutive login failures per user.
 *   CAPTCHA provider defaults to `noop` (disabled) — set to `altcha`, `turnstile`, `hcaptcha`, or `recaptcha` in production.
 * - MFA token TTL: `mfa.mfaTokenTtlSeconds` (default: 300s / 5 minutes) — JWT challenge token lifetime.
 * - OTP TTL: `mfa.otpTtlSeconds` (default: 300s / 5 minutes) — Redis-backed OTP code lifetime.
 * - Trusted device: `mfa.trustedDeviceTtlDays` (default: 30 days) — SHA-256 hash on UserEntity.
 *   TTL enforcement implemented in User.requiresMfa() via trustedDeviceSetAt column (V10 migration).
 *   Used by LoginHandler (CQRS path) and AuthService (legacy path, @Deprecated).
 * - JWT: RS256 asymmetric signing (primary), HMAC-SHA256 fallback for legacy migration (7-day window).
 * - Password: BCrypt strength 12, account locks after 3 failed attempts for 15 minutes.
 */
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val enabled: Boolean = true,
    val jwt: JwtProperties = JwtProperties(),
    val password: PasswordProperties = PasswordProperties(),
    val mfa: MfaProperties = MfaProperties(),
    val captcha: CaptchaProperties = CaptchaProperties(),
    val sso: SsoProperties = SsoProperties(),
    val loginRateLimit: LoginRateLimitProperties = LoginRateLimitProperties(),
    val session: SessionProperties = SessionProperties(),
    val anonymous: AnonymousProperties = AnonymousProperties(),
    /** Token blacklist cache configuration (L1 Caffeine + L2 Redis). */
    val blacklist: BlacklistCacheProperties = BlacklistCacheProperties(),
    /** Validation event recording configuration. */
    val validationEvent: ValidationEventProperties = ValidationEventProperties()
) {
    /**
     * JWT signing and token lifetime configuration.
     * @property secretKey HMAC secret key (legacy fallback only, ignored when algorithm=RS256).
     * @property algorithm Signing algorithm — `RS256` (asymmetric, recommended) or `HS256` (symmetric, legacy).
     * @property privateKeyPath Path to RSA private key PEM file (required for RS256).
     * @property publicKeyPath Path to RSA public key PEM file (required for RS256).
     * @property keyId Key ID (`kid`) header in JWT — used for JWKS key rotation.
     * @property accessTokenExpirationMs Access token lifetime — default 900,000ms (15 minutes, sliding window).
     * @property refreshTokenExpirationMs Refresh token lifetime — default 604,800,000ms (7 days).
     * @property absoluteCeilingMs Absolute session ceiling — default 36,000,000ms (10 hours).
     * @property issuer JWT `iss` claim value.
     */
    data class JwtProperties(
        val secretKey: String = "",
        val algorithm: String = "RS256",
        val privateKeyPath: String = "",
        val publicKeyPath: String = "",
        val keyId: String = "auth-service-key-1",
        val accessTokenExpirationMs: Long = 900_000,       // 15 min (sliding window)
        val refreshTokenExpirationMs: Long = 604_800_000,   // 7 days
        val absoluteCeilingMs: Long = 36_000_000,            // 10 hours
        val issuer: String = "auth-service",
        /** Clock skew tolerance in seconds for exp/nbf validation (RFC 8725). Default 60s. */
        val clockSkewSeconds: Long = 60,
        /** Audience claim value. Empty = disabled (no aud validation). Non-empty = validated + added to generated tokens. */
        val audience: String = "",
        /** Path to previous RSA public key PEM file for key rotation overlap period. Empty = single key. */
        val previousPublicKeyPath: String = "",
        /** Key ID for previous RSA key (used during key rotation overlap). */
        val previousKeyId: String = ""
    )

    /**
     * Password security configuration.
     * @property bcryptStrength BCrypt hashing strength — default 12 (recommended 10-14).
     * @property maxFailedAttempts Failed login attempts before account lock / CAPTCHA trigger — default 3.
     * @property lockDurationMinutes Account lock duration after max failed attempts — default 15 minutes.
     */
    data class PasswordProperties(
        val bcryptStrength: Int = 12,
        val maxFailedAttempts: Int = 3,
        val lockDurationMinutes: Int = 15
    )

    /**
     * Multi-Factor Authentication configuration.
     * @property otpTtlSeconds OTP code lifetime in Redis — default 300s (5 minutes). Key format: `otp:{userId}:{channel}`.
     * @property maxAttempts Max OTP verification attempts per MFA session — default 3. Exceeding throws MfaMaxAttemptsException.
     * @property totpWindow TOTP time-step drift tolerance — default 1 (±30 seconds). Uses dev.samstevens.totp library.
     * @property mfaTokenTtlSeconds MFA challenge JWT token lifetime — default 300s (5 minutes). Contains userId + method claims.
     * @property trustedDeviceTtlDays Trusted device validity period — default 30 days.
     *   SHA-256 hash on UserEntity.trustedDeviceHash. TTL enforcement implemented in
     *   User.requiresMfa() via trustedDeviceSetAt column (V10 migration). Default: 30 days.
     */
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

    /**
     * CAPTCHA configuration — pluggable provider pattern.
     * CAPTCHA is required after [PasswordProperties.maxFailedAttempts] (default: 3) consecutive login failures.
     * @property provider Active CAPTCHA provider — `noop` (disabled/dev), `altcha` (PoW), `turnstile`, `hcaptcha`, `recaptcha`.
     *   Default: `noop` — **must be changed for production**.
     * @property secretKey Provider-specific secret key (env: `CAPTCHA_SECRET_KEY`).
     * @property siteKey Provider-specific site/public key (env: `CAPTCHA_SITE_KEY`).
     * @property verifyUrl Provider server-side verification URL.
     * @property altcha ALTCHA Proof-of-Work specific config (self-hosted, no third-party dependency).
     */
    data class CaptchaProperties(
        val provider: String = "noop",
        val secretKey: String = "",
        val siteKey: String = "",
        val verifyUrl: String = "",
        val altcha: AltchaProperties = AltchaProperties()
    ) {
        /**
         * ALTCHA Proof-of-Work CAPTCHA configuration.
         * @property hmacKey HMAC key for challenge signing — **must be ≥32 chars in production**.
         * @property difficulty SHA-256 iteration count for PoW — default 50,000.
         * @property challengeTtlSeconds Challenge validity period — default 300s (5 minutes).
         */
        data class AltchaProperties(
            val hmacKey: String = "",
            val difficulty: Int = 50_000,
            val challengeTtlSeconds: Long = 300
        )
    }

    data class SsoProperties(
        val enabled: Boolean = false,
        val autoProvisionEnabled: Boolean = false,
        val defaultDomainCode: String = "default",
        val timeoutMs: Long = 10_000,
        /** Config-driven SSO provider endpoints — keyed by provider ID (e.g., "google", "keycloak"). */
        val providers: Map<String, ProviderConfig> = emptyMap()
    ) {
        /**
         * Per-provider OAuth2/OIDC endpoint configuration.
         * Allows runtime addition of providers (e.g., Keycloak) without code changes.
         */
        data class ProviderConfig(
            val tokenEndpoint: String,
            val userInfoEndpoint: String,
            val clientId: String = "",
            val clientSecret: String = "",
            val enabled: Boolean = true
        )
    }

    /**
     * Multi-dimensional login rate limiting configuration.
     * Applied via LoginRateLimitFilter before authentication handler.
     */
    data class LoginRateLimitProperties(
        val ip: MfaProperties.LimitConfig = MfaProperties.LimitConfig(
            maxAttempts = 5, windowSeconds = 60, lockSeconds = 300
        ),
        val username: MfaProperties.LimitConfig = MfaProperties.LimitConfig(
            maxAttempts = 3, windowSeconds = 900, lockSeconds = 1800
        ),
        val device: MfaProperties.LimitConfig = MfaProperties.LimitConfig(
            maxAttempts = 10, windowSeconds = 3600, lockSeconds = 3600
        ),
        val redisTimeoutMs: Long = 500
    )

    /**
     * Configurable session policy — per-role overrides supported.
     */
    data class SessionProperties(
        val maxSessions: Int = 3,
        val maxDevices: Int = 3,
        val onExceed: SessionExceedStrategy = SessionExceedStrategy.REVOKE_OLDEST,
        val roleOverrides: Map<String, SessionOverride> = emptyMap(),
        val inactivityTimeoutMinutes: Long = 30,
        val cleanupCronExpression: String = "0 */5 * * * *"
    ) {
        enum class SessionExceedStrategy {
            REVOKE_OLDEST, REJECT_NEW, REVOKE_ALL
        }

        data class SessionOverride(
            val maxSessions: Int? = null,
            val maxDevices: Int? = null,
            val onExceed: SessionExceedStrategy? = null
        )
    }

    /**
     * Anonymous/guest session configuration.
     * Supports ephemeral sessions stored in Redis with configurable TTL and rate limiting.
     */
    data class AnonymousProperties(
        val tokenTtlSeconds: Long = 3600,             // 1 hour
        val sessionTtlSeconds: Long = 86400,           // 24 hours
        val maxDataSizeBytes: Long = 65536,            // 64KB
        val maxRenewals: Int = 24,
        val promotedDataTtlSeconds: Long = 604800,     // 7 days
        val rateLimit: MfaProperties.LimitConfig = MfaProperties.LimitConfig(
            maxAttempts = 5,
            windowSeconds = 3600,
            lockSeconds = 0
        ),
        /** Feature flag: enable sliding window rate limiting (Lua script). When false, falls back to fixed-window INCR+EXPIRE. */
        val slidingWindowEnabled: Boolean = true,
        /** SCAN batch size for Redis key iteration (e.g., data transfer, cleanup). Tunable for large sessions. */
        val scanCount: Int = 100
    )

    /**
     * Token blacklist cache configuration (FR-001, FR-015, FR-016).
     * L1 Caffeine (in-process) + L2 Redis (distributed) + DB fallback.
     */
    data class BlacklistCacheProperties(
        /** L1 Caffeine cache TTL in seconds. Default 30s — max acceptance window for revoked token. */
        val caffeineTtlSeconds: Long = 30,
        /** L1 Caffeine cache maximum entries. */
        val caffeineMaxSize: Long = 10_000,
        /** Redis operation timeout in milliseconds. */
        val redisTimeoutMs: Long = 200,
        /** Redis key prefix for blacklisted token JTIs. */
        val redisKeyPrefix: String = "token:blacklist:",
        /** Number of consecutive Redis failures before circuit breaker opens. */
        val circuitBreakerThreshold: Int = 5,
        /** Circuit breaker reset duration in seconds after opening. */
        val circuitBreakerResetSeconds: Long = 30
    )

    /**
     * Validation event recording configuration (FR-012).
     */
    data class ValidationEventProperties(
        /** Whether to record events for expired tokens (routine). Default false — only suspicious patterns. */
        val recordExpiredEvents: Boolean = false
    )
}
