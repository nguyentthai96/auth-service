package com.ntt.accountservice.preference.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * User preference entity — key-value grouped by category (FR-006).
 * Supports UI settings, notification preferences, privacy settings.
 */
@Entity
@Table(
    name = "user_preferences",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "category", "pref_key"])]
)
class UserPreferenceEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(nullable = false, length = 50)
    lateinit var category: String

    @Column(name = "pref_key", nullable = false, length = 100)
    lateinit var prefKey: String

    @Column(name = "pref_value", nullable = false, length = 2000)
    lateinit var prefValue: String

    @Column(name = "value_type", nullable = false, length = 20)
    var valueType: String = "STRING"
}
