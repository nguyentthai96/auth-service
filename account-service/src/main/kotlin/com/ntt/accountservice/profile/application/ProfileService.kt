package com.ntt.accountservice.profile.application

import com.ntt.accountservice.profile.adapter.`in`.web.dto.ProfileResponse
import com.ntt.accountservice.profile.adapter.`in`.web.dto.UpdateProfileRequest
import com.ntt.accountservice.profile.adapter.out.persistence.entity.UserContactEntity
import com.ntt.accountservice.profile.adapter.out.persistence.entity.UserProfileEntity
import com.ntt.accountservice.shared.exception.AccountException
import com.ntt.accountservice.shared.exception.AccountErrorCode
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Profile service — CRUD operations for user profile (FR-005).
 * Clean Architecture: controller → service → entity.
 */
@Service
class ProfileService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(ProfileService::class.java)

    /**
     * Get user profile with contacts.
     */
    fun getProfile(userId: Long): ProfileResponse {
        val profile = findProfileByUserId(userId)
            ?: throw AccountException(AccountErrorCode.PROFILE_NOT_FOUND, "Profile not found for userId=$userId")

        val contacts = entityManager
            .createQuery("SELECT c FROM UserContactEntity c WHERE c.userId = :userId", UserContactEntity::class.java)
            .setParameter("userId", userId)
            .resultList

        return ProfileResponse.from(profile, contacts)
    }

    /**
     * Update user profile — partial update semantics.
     */
    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): ProfileResponse {
        val profile = findProfileByUserId(userId)
            ?: throw AccountException(AccountErrorCode.PROFILE_NOT_FOUND, "Profile not found for userId=$userId")

        request.displayName?.let { profile.displayName = it }
        request.firstName?.let { profile.firstName = it }
        request.lastName?.let { profile.lastName = it }
        request.dateOfBirth?.let { profile.dateOfBirth = it }
        request.address?.let { profile.address = it }
        request.timezone?.let { profile.timezone = it }
        request.locale?.let { profile.locale = it }
        request.avatarUrl?.let { profile.avatarUrl = it }
        request.bio?.let { profile.bio = it }

        entityManager.merge(profile)

        log.info("Profile updated for userId={}", userId)

        val contacts = entityManager
            .createQuery("SELECT c FROM UserContactEntity c WHERE c.userId = :userId", UserContactEntity::class.java)
            .setParameter("userId", userId)
            .resultList

        return ProfileResponse.from(profile, contacts)
    }

    /**
     * Create default profile for a newly registered user (called by Kafka listener).
     */
    @Transactional
    fun createDefaultProfile(userId: Long, username: String, domainCode: String): UserProfileEntity {
        val existing = findProfileByUserId(userId)
        if (existing != null) {
            log.warn("Profile already exists for userId={}, skipping creation", userId)
            return existing
        }

        val profile = UserProfileEntity().apply {
            this.userId = userId
            this.displayName = username
            this.timezone = "UTC"
            this.locale = "en"
        }
        entityManager.persist(profile)
        log.info("Default profile created for userId={}, domain={}", userId, domainCode)
        return profile
    }

    private fun findProfileByUserId(userId: Long): UserProfileEntity? {
        return entityManager
            .createQuery("SELECT p FROM UserProfileEntity p WHERE p.userId = :userId", UserProfileEntity::class.java)
            .setParameter("userId", userId)
            .resultList
            .firstOrNull()
    }
}
