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
    val password: PasswordProperties = PasswordProperties()
) {
    data class JwtProperties(
        val secretKey: String = "",
        val accessTokenExpirationMs: Long = 1_800_000,   // 30 min
        val refreshTokenExpirationMs: Long = 604_800_000, // 7 days
        val issuer: String = "auth-service"
    )

    data class PasswordProperties(
        val bcryptStrength: Int = 12,
        val maxFailedAttempts: Int = 3,
        val lockDurationMinutes: Int = 15
    )
}
