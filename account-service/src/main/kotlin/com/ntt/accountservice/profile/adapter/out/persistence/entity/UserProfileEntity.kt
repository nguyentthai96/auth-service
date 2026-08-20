package com.ntt.accountservice.profile.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import java.time.LocalDate

/**
 * User profile entity — stores personal information separate from auth data (FR-005).
 */
@Entity
@Table(name = "user_profiles")
class UserProfileEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long = 0

    @Column(name = "display_name", length = 200)
    var displayName: String? = null

    @Column(name = "first_name", length = 100)
    var firstName: String? = null

    @Column(name = "last_name", length = 100)
    var lastName: String? = null

    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null

    @Column(length = 500)
    var address: String? = null

    @Column(length = 50)
    var timezone: String = "UTC"

    @Column(length = 10)
    var locale: String = "en"

    @Column(name = "avatar_url", length = 1000)
    var avatarUrl: String? = null

    @Column(length = 500)
    var bio: String? = null
}
