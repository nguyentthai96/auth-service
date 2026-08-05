package com.ntt.authservice.auth.adapter.out.cache

import com.ntt.authservice.auth.application.port.out.PermissionCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Multi-tier permission cache: L1 (Caffeine) → L2 (Redis).
 *
 * Read path: Check L1 → miss? → Check L2 → miss? → return null (caller loads from DB + puts back).
 * Write path: Put in L1 + L2 simultaneously.
 * Invalidate path: Evict from L1 + L2.
 *
 * Note: @Primary is on CaffeinePermissionCache. This class is used when L2 is explicitly needed.
 * To make this the primary cache, swap @Primary annotation.
 */
@Component("multiTierPermissionCache")
class MultiTierPermissionCache(
    private val caffeineCache: CaffeinePermissionCache,
    private val redisTemplate: RedisTemplate<String, Any>,
    @Value("\${app.security.cache.permission.l2-ttl-seconds:1800}")
    private val l2TtlSeconds: Long,
    @Value("\${app.security.cache.permission.l2-key-prefix:auth:perm:}")
    private val keyPrefix: String
) : PermissionCache {

    private val log = LoggerFactory.getLogger(MultiTierPermissionCache::class.java)

    private fun redisKey(userId: Long, domainId: Long, type: String) =
        "$keyPrefix$type:$userId:$domainId"

    @Suppress("UNCHECKED_CAST")
    override fun getPermissions(userId: Long, domainId: Long): List<String>? {
        // L1 check
        caffeineCache.getPermissions(userId, domainId)?.let { return it }

        // L2 check
        return try {
            val key = redisKey(userId, domainId, "perms")
            val cached = redisTemplate.opsForValue().get(key) as? List<String>
            if (cached != null) {
                caffeineCache.putPermissions(userId, domainId, cached) // backfill L1
            }
            cached
        } catch (ex: Exception) {
            log.warn("Redis L2 getPermissions failed, falling back to DB: {}", ex.message)
            null
        }
    }

    override fun putPermissions(userId: Long, domainId: Long, permissions: List<String>) {
        caffeineCache.putPermissions(userId, domainId, permissions)
        try {
            redisTemplate.opsForValue().set(
                redisKey(userId, domainId, "perms"),
                permissions,
                l2TtlSeconds,
                TimeUnit.SECONDS
            )
        } catch (ex: Exception) {
            log.warn("Redis L2 putPermissions failed: {}", ex.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getRoles(userId: Long, domainId: Long): List<String>? {
        caffeineCache.getRoles(userId, domainId)?.let { return it }
        return try {
            val key = redisKey(userId, domainId, "roles")
            val cached = redisTemplate.opsForValue().get(key) as? List<String>
            if (cached != null) {
                caffeineCache.putRoles(userId, domainId, cached) // backfill L1
            }
            cached
        } catch (ex: Exception) {
            log.warn("Redis L2 getRoles failed: {}", ex.message)
            null
        }
    }

    override fun putRoles(userId: Long, domainId: Long, roles: List<String>) {
        caffeineCache.putRoles(userId, domainId, roles)
        try {
            redisTemplate.opsForValue().set(
                redisKey(userId, domainId, "roles"),
                roles,
                l2TtlSeconds,
                TimeUnit.SECONDS
            )
        } catch (ex: Exception) {
            log.warn("Redis L2 putRoles failed: {}", ex.message)
        }
    }

    override fun invalidate(userId: Long, domainId: Long) {
        caffeineCache.invalidate(userId, domainId)
        try {
            redisTemplate.delete(redisKey(userId, domainId, "perms"))
            redisTemplate.delete(redisKey(userId, domainId, "roles"))
        } catch (ex: Exception) {
            log.warn("Redis L2 invalidate failed: {}", ex.message)
        }
    }

    override fun invalidateAll() {
        caffeineCache.invalidateAll()
        try {
            val keys = redisTemplate.keys("${keyPrefix}*")
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
            }
        } catch (ex: Exception) {
            log.warn("Redis L2 invalidateAll failed: {}", ex.message)
        }
    }
}
