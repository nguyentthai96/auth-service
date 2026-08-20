package com.ntt.accountservice.profile.adapter.`in`.web.dto

import com.ntt.accountservice.profile.adapter.out.persistence.entity.UserContactEntity
import com.ntt.accountservice.profile.adapter.out.persistence.entity.UserProfileEntity
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * Profile DTOs — data class + companion from() factory pattern.
 */
data class ProfileResponse(
    val userId: Long,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val dateOfBirth: LocalDate?,
    val address: String?,
    val timezone: String,
    val locale: String,
    val avatarUrl: String?,
    val bio: String?,
    val contacts: List<ContactResponse>
) {
    companion object {
        fun from(profile: UserProfileEntity, contacts: List<UserContactEntity>): ProfileResponse {
            return ProfileResponse(
                userId = profile.userId,
                displayName = profile.displayName,
                firstName = profile.firstName,
                lastName = profile.lastName,
                dateOfBirth = profile.dateOfBirth,
                address = profile.address,
                timezone = profile.timezone,
                locale = profile.locale,
                avatarUrl = profile.avatarUrl,
                bio = profile.bio,
                contacts = contacts.map { ContactResponse.from(it) }
            )
        }
    }
}

data class UpdateProfileRequest(
    @field:Size(max = 200) val displayName: String? = null,
    @field:Size(max = 100) val firstName: String? = null,
    @field:Size(max = 100) val lastName: String? = null,
    val dateOfBirth: LocalDate? = null,
    @field:Size(max = 500) val address: String? = null,
    @field:Size(max = 50) val timezone: String? = null,
    @field:Size(max = 10) val locale: String? = null,
    @field:Size(max = 1000) val avatarUrl: String? = null,
    @field:Size(max = 500) val bio: String? = null
)

data class ContactResponse(
    val id: Long?,
    val contactType: String,
    val contactValue: String,
    val verified: Boolean,
    val isPrimary: Boolean
) {
    companion object {
        fun from(entity: UserContactEntity): ContactResponse {
            return ContactResponse(
                id = entity.id,
                contactType = entity.contactType,
                contactValue = entity.contactValue,
                verified = entity.verified,
                isPrimary = entity.isPrimary
            )
        }
    }
}
