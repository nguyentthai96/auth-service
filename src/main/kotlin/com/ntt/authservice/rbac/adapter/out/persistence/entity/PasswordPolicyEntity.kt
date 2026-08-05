package com.ntt.authservice.rbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakeBaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Domain-scoped password complexity policy.
 * Each domain can have one policy; falls back to system defaults if absent.
 */
@Entity
@Table(name = "password_policies")
class PasswordPolicyEntity : SnowflakeBaseEntity() {

    @Column(name = "domain_id", nullable = false, unique = true)
    var domainId: Long = 0

    @Column(name = "min_length", nullable = false)
    var minLength: Int = 8

    @Column(name = "max_length", nullable = false)
    var maxLength: Int = 128

    @Column(name = "require_uppercase", nullable = false)
    var requireUppercase: Boolean = true

    @Column(name = "require_lowercase", nullable = false)
    var requireLowercase: Boolean = true

    @Column(name = "require_digit", nullable = false)
    var requireDigit: Boolean = true

    @Column(name = "require_special", nullable = false)
    var requireSpecial: Boolean = false

    @Column(name = "min_character_types", nullable = false)
    var minCharacterTypes: Int = 3

    @Column(name = "history_count", nullable = false)
    var historyCount: Int = 5

    @Column(name = "max_age_days", nullable = false)
    var maxAgeDays: Int = 90

    @Column(name = "lockout_threshold", nullable = false)
    var lockoutThreshold: Int = 5

    @Column(name = "lockout_duration_minutes", nullable = false)
    var lockoutDurationMinutes: Int = 15

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
