package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Transparent password hash upgrade service — rehashes legacy passwords on successful login.
 *
 * Uses DelegatingPasswordEncoder.upgradeEncoding() to detect if the current hash
 * was NOT produced by the default encoder (e.g., BCrypt hash when default is Argon2id).
 * When detected, re-encodes the raw password with the default encoder and updates
 * the User domain model. The caller (LoginHandler) persists the change via userPort.save().
 *
 * This enables zero-downtime migration from BCrypt to Argon2id without forced password resets.
 *
 * Audit: Records PASSWORD_REHASHED event via AuditLogService.
 * Monitoring: Increments "password.migration.rehash.total" Micrometer counter.
 */
@Service
class PasswordUpgradeService(
    private val passwordEncoder: PasswordEncoder,
    private val auditLogService: AuditLogService,
    meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(PasswordUpgradeService::class.java)

    private val rehashCounter: Counter = Counter.builder("password.migration.rehash.total")
        .description("Total password rehash operations from legacy algorithm to default")
        .register(meterRegistry)

    /**
     * Check if the user's password hash needs upgrading and perform the rehash if needed.
     *
     * Must be called AFTER successful password verification but BEFORE userPort.save()
     * so that both resetFailedLogins() and hash upgrade are persisted in the same transaction.
     *
     * @param user User domain model — passwordHash will be mutated if upgrade needed
     * @param rawPassword The verified raw password (available only during login flow)
     */
    fun upgradeIfNeeded(user: User, rawPassword: String) {
        if (passwordEncoder.upgradeEncoding(user.passwordHash.value)) {
            val newHash = passwordEncoder.encode(rawPassword)
                ?: throw IllegalStateException("PasswordEncoder.encode() returned null")
            user.passwordHash = PasswordHash(newHash)

            auditLogService.logEvent(
                user.id.value,
                AuditAction.PASSWORD_REHASHED,
                "User",
                user.id.value.toString(),
                "algorithm=argon2id"
            )
            rehashCounter.increment()

            log.info("Password rehashed for userId={} from legacy to default algorithm", user.id.value)
        }
    }
}
