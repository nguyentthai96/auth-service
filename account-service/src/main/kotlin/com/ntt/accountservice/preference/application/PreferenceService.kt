package com.ntt.accountservice.preference.application

import com.ntt.accountservice.preference.adapter.out.persistence.entity.UserPreferenceEntity
import com.ntt.accountservice.shared.exception.AccountErrorCode
import com.ntt.accountservice.shared.exception.AccountException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Preference service — key-value settings grouped by category (FR-006).
 * Supports merge-update semantics (PATCH = partial update within category).
 */
@Service
class PreferenceService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(PreferenceService::class.java)

    companion object {
        val VALID_CATEGORIES = setOf("ui", "notification", "privacy", "accessibility", "general")
        val VALID_VALUE_TYPES = setOf("STRING", "BOOLEAN", "NUMBER", "JSON")
    }

    /**
     * Get all preferences for a user, grouped by category.
     */
    fun getAllPreferences(userId: Long): Map<String, Map<String, String>> {
        val prefs = entityManager
            .createQuery("SELECT p FROM UserPreferenceEntity p WHERE p.userId = :userId AND p.active = true", UserPreferenceEntity::class.java)
            .setParameter("userId", userId)
            .resultList
        return prefs.groupBy { it.category }
            .mapValues { (_, entries) -> entries.associate { it.prefKey to it.prefValue } }
    }

    /**
     * Get preferences for a specific category.
     */
    fun getPreferencesByCategory(userId: Long, category: String): Map<String, String> {
        validateCategory(category)
        val prefs = entityManager
            .createQuery("SELECT p FROM UserPreferenceEntity p WHERE p.userId = :userId AND p.category = :cat AND p.active = true", UserPreferenceEntity::class.java)
            .setParameter("userId", userId)
            .setParameter("cat", category)
            .resultList
        return prefs.associate { it.prefKey to it.prefValue }
    }

    /**
     * Merge-update preferences within a category (PATCH semantics).
     * Only updates provided keys; existing keys not in the request are preserved.
     */
    @Transactional
    fun updatePreferences(userId: Long, category: String, preferences: Map<String, String>): Map<String, String> {
        validateCategory(category)

        preferences.forEach { (key, value) ->
            val existing = entityManager
                .createQuery("SELECT p FROM UserPreferenceEntity p WHERE p.userId = :userId AND p.category = :cat AND p.prefKey = :key AND p.active = true", UserPreferenceEntity::class.java)
                .setParameter("userId", userId)
                .setParameter("cat", category)
                .setParameter("key", key)
                .resultList
                .firstOrNull()

            if (existing != null) {
                existing.prefValue = value
                entityManager.merge(existing)
            } else {
                val pref = UserPreferenceEntity().apply {
                    this.userId = userId
                    this.category = category
                    this.prefKey = key
                    this.prefValue = value
                }
                entityManager.persist(pref)
            }
        }

        log.info("Preferences updated: userId={}, category={}, keys={}", userId, category, preferences.keys)
        return getPreferencesByCategory(userId, category)
    }

    private fun validateCategory(category: String) {
        if (category !in VALID_CATEGORIES) {
            throw AccountException(AccountErrorCode.INVALID_PREFERENCE_FORMAT, "Invalid category: $category. Valid: $VALID_CATEGORIES")
        }
    }
}
