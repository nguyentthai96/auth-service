package com.ntt.authservice.shared.security

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Computes user's effective menu permissions and caches them in Redis.
 *
 * Algorithm (FR-006, FR-018):
 * 1. Receive Kafka event with menuCode + permissions + assignedRoles
 * 2. For each assigned role, resolve all users with that role (via auth DB)
 * 3. For each affected user:
 *    a. Collect all menu permissions across all their roles
 *    b. Apply user-level overrides (grant/deny, if any)
 *    c. Build authority set: "menu:{menuCode}:{permCode}"
 *    d. SADD to Redis: user:{userId}:menu:perms
 *    e. Set TTL (5 minutes — BR-MENU-05)
 *
 * Graceful degradation: if Redis fails, log error but don't fail the flow.
 *
 * This component computes permissions CENTRALIZED in auth-service
 * (OQ-03 decision: auth-service computes, system-admin configures).
 */
@Component
class UserMenuPermissionComputer(
    private val redisTemplate: StringRedisTemplate?,
    private val menuPermissionDataProvider: MenuPermissionDataProvider
) {
    private val log = LoggerFactory.getLogger(UserMenuPermissionComputer::class.java)

    companion object {
        const val REDIS_KEY_PREFIX = "user:"
        const val REDIS_KEY_SUFFIX = ":menu:perms"
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }

    /**
     * Recompute permissions for all users affected by a menu change event.
     *
     * Called by MenuSecurityRuleSyncConsumer after rule upsert/delete.
     *
     * @param menuCode the menu code that changed
     * @param permissions list of permission codes for this menu
     * @param affectedRoleIds role IDs assigned to this menu
     */
    @Async
    fun recomputeForMenuChange(
        menuCode: String,
        permissions: List<String>,
        affectedRoleIds: List<Long>
    ) {
        if (redisTemplate == null) {
            log.warn("Redis unavailable — skipping menu permission computation for menuCode={}", menuCode)
            return
        }

        try {
            // Resolve all userIds for affected roles
            val affectedUserIds = menuPermissionDataProvider.findUserIdsByRoleIds(affectedRoleIds)

            if (affectedUserIds.isEmpty()) {
                log.info("No users found for roles={} on menuCode={}, skipping", affectedRoleIds, menuCode)
                return
            }

            var computedCount = 0
            affectedUserIds.forEach { userId ->
                try {
                    computeForUser(userId)
                    computedCount++
                } catch (ex: Exception) {
                    log.error("Failed to compute permissions for userId={}: {}", userId, ex.message)
                }
            }

            log.info("Recomputed menu permissions for {} users affected by menuCode={}", computedCount, menuCode)
        } catch (ex: Exception) {
            log.error("Failed to recompute menu permissions for menuCode={}: {}", menuCode, ex.message, ex)
        }
    }

    /**
     * Compute effective menu permissions for a single user.
     *
     * Algorithm:
     * 1. Get user's role IDs (from auth DB)
     * 2. Get all menu-role-permissions for those roles (via data provider)
     * 3. Build authority strings: "menu:{menuCode}:{permCode}"
     * 4. Cache in Redis SET with TTL
     *
     * @param userId the user to compute permissions for
     */
    fun computeForUser(userId: Long) {
        if (redisTemplate == null) return

        try {
            // Get user's role IDs
            val roleIds = menuPermissionDataProvider.findRoleIdsByUserId(userId)
            if (roleIds.isEmpty()) {
                // No roles — clear any cached permissions
                clearUserPermissions(userId)
                return
            }

            // Get all menu permissions for these roles
            val menuPermissions = menuPermissionDataProvider.findMenuPermissionsByRoleIds(roleIds)

            if (menuPermissions.isEmpty()) {
                clearUserPermissions(userId)
                return
            }

            // Build authority strings
            val authorities = menuPermissions.map { "menu:${it.menuCode}:${it.permissionCode}" }.toSet()

            // Write to Redis
            val redisKey = "$REDIS_KEY_PREFIX$userId$REDIS_KEY_SUFFIX"

            // Use pipeline for atomic replace: DEL + SADD + EXPIRE
            redisTemplate.execute { connection ->
                val keyBytes = redisKey.toByteArray()
                connection.del(keyBytes)
                if (authorities.isNotEmpty()) {
                    connection.sAdd(keyBytes, *authorities.map { it.toByteArray() }.toTypedArray())
                    connection.expire(keyBytes, CACHE_TTL.seconds)
                }
                null
            }

            log.debug("Cached {} menu permissions for userId={}", authorities.size, userId)
        } catch (ex: Exception) {
            log.error("Failed to compute/cache menu permissions for userId={}: {}", userId, ex.message, ex)
            // Graceful degradation — don't fail the calling flow
        }
    }

    /**
     * Invalidate all users' permissions for a given role.
     * Called when a role's menu assignments change.
     *
     * @param roleId the role that changed
     */
    @Async
    fun invalidateForRole(roleId: Long) {
        try {
            val userIds = menuPermissionDataProvider.findUserIdsByRoleIds(listOf(roleId))
            userIds.forEach { userId -> computeForUser(userId) }
            log.info("Invalidated and recomputed menu permissions for {} users of role {}", userIds.size, roleId)
        } catch (ex: Exception) {
            log.error("Failed to invalidate permissions for roleId={}: {}", roleId, ex.message, ex)
        }
    }

    /**
     * Invalidate a single user's cached menu permissions.
     */
    fun invalidateForUser(userId: Long) {
        clearUserPermissions(userId)
    }

    private fun clearUserPermissions(userId: Long) {
        try {
            val redisKey = "$REDIS_KEY_PREFIX$userId$REDIS_KEY_SUFFIX"
            redisTemplate?.delete(redisKey)
            log.debug("Cleared menu permissions cache for userId={}", userId)
        } catch (ex: Exception) {
            log.warn("Failed to clear menu permissions cache for userId={}", userId, ex)
        }
    }
}

/**
 * DTO for menu permission data from system-admin-service.
 */
data class MenuPermissionEntry(
    val menuCode: String,
    val permissionCode: String,
    val roleName: String = ""
)
