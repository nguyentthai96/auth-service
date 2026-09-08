package com.ntt.authservice.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Auto-configuration for PasswordEncoder — selects hashing algorithm based on YAML property.
 *
 * Replaces the hardcoded BCryptPasswordEncoder bean previously defined in SecurityConfig.
 * Uses DelegatingPasswordEncoder for:
 *   - Prefix-based routing ({bcrypt}, {argon2id}) for backward compatibility
 *   - Built-in upgradeEncoding() for transparent rehash detection
 *   - setDefaultPasswordEncoderForMatches() for bare hashes (pre-migration safety net)
 *
 * Wrapped in ConcurrencyLimitedPasswordEncoder to prevent OOM under concurrent Argon2id load.
 *
 * @see ConcurrencyLimitedPasswordEncoder
 * @see SecurityProperties.PasswordProperties
 */
@Configuration
class PasswordEncoderAutoConfiguration(
    private val securityProperties: SecurityProperties
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val passwordProps = securityProperties.password
        val argon2Props = passwordProps.argon2

        // Build encoder map — always include both for backward compatibility
        val encoders = mapOf<String, PasswordEncoder>(
            "bcrypt" to BCryptPasswordEncoder(passwordProps.bcryptStrength),
            "argon2id" to Argon2PasswordEncoder(
                argon2Props.saltLength,
                argon2Props.hashLength,
                argon2Props.parallelism,
                argon2Props.memoryCost,
                argon2Props.iterations
            )
        )

        // Default encoder = configured algorithm (argon2id or bcrypt)
        val delegate = DelegatingPasswordEncoder(passwordProps.algorithm, encoders).apply {
            // Fallback for hashes without prefix (legacy BCrypt before Flyway migration)
            setDefaultPasswordEncoderForMatches(
                BCryptPasswordEncoder(passwordProps.bcryptStrength)
            )
        }

        // Wrap with concurrency limiter to prevent OOM under parallel Argon2id load
        return ConcurrencyLimitedPasswordEncoder(
            delegate = delegate,
            maxConcurrent = passwordProps.maxConcurrentHashes
        )
    }
}
