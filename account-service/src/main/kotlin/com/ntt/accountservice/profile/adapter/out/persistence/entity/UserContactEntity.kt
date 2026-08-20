package com.ntt.accountservice.profile.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * User contact information entity — supports multiple contact methods (FR-005).
 * Email/phone changes require verification flow.
 */
@Entity
@Table(
    name = "user_contacts",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "contact_type", "contact_value"])]
)
class UserContactEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    /** Contact type: EMAIL, PHONE, SECONDARY_EMAIL */
    @Column(name = "contact_type", nullable = false, length = 30)
    lateinit var contactType: String

    @Column(name = "contact_value", nullable = false, length = 255)
    lateinit var contactValue: String

    @Column(nullable = false)
    var verified: Boolean = false

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false
}
