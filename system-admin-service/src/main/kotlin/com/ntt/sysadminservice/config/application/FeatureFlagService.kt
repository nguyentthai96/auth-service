package com.ntt.sysadminservice.config.application

import com.ntt.sysadminservice.config.adapter.out.persistence.entity.FeatureFlagEntity
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature flag service — boolean + percentage rollout + user segment targeting (FR-014).
 */
@Service
class FeatureFlagService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(FeatureFlagService::class.java)

    /**
     * Get all feature flags for a domain.
     */
    fun getFlags(domainId: Long): List<FeatureFlagEntity> {
        return entityManager
            .createQuery("SELECT f FROM FeatureFlagEntity f WHERE f.domainId = :domainId AND f.active = true", FeatureFlagEntity::class.java)
            .setParameter("domainId", domainId)
            .resultList
    }

    /**
     * Check if a feature flag is enabled for a user.
     */
    fun isEnabled(domainId: Long, flagKey: String, userId: Long? = null): Boolean {
        val flag = findByKey(domainId, flagKey) ?: return false
        if (!flag.enabled) return false

        // 100% rollout = enabled for all
        if (flag.rolloutPct >= 100) return true

        // Percentage-based rollout using consistent hashing
        if (userId != null && flag.rolloutPct > 0) {
            val hash = (userId.hashCode() and Int.MAX_VALUE) % 100
            return hash < flag.rolloutPct
        }

        return flag.rolloutPct >= 100
    }

    /**
     * Update a feature flag.
     */
    @Transactional
    fun updateFlag(domainId: Long, flagKey: String, enabled: Boolean?, rolloutPct: Int?, description: String?): FeatureFlagEntity {
        val flag = findByKey(domainId, flagKey)
            ?: throw SysAdminException(SysAdminErrorCode.CONFIG_NOT_FOUND, "Feature flag not found: $flagKey")

        enabled?.let { flag.enabled = it }
        rolloutPct?.let {
            if (it < 0 || it > 100) throw SysAdminException(SysAdminErrorCode.INVALID_CONFIG_TYPE, "Rollout percentage must be 0-100")
            flag.rolloutPct = it
        }
        description?.let { flag.description = it }

        log.info("Feature flag updated: domain={}, key={}, enabled={}, rollout={}%", domainId, flagKey, flag.enabled, flag.rolloutPct)
        return entityManager.merge(flag)
    }

    private fun findByKey(domainId: Long, flagKey: String): FeatureFlagEntity? {
        return entityManager
            .createQuery("SELECT f FROM FeatureFlagEntity f WHERE f.domainId = :domainId AND f.flagKey = :key AND f.active = true", FeatureFlagEntity::class.java)
            .setParameter("domainId", domainId)
            .setParameter("key", flagKey)
            .resultList
            .firstOrNull()
    }
}
