package com.ntt.authservice.auth.adapter.out.cache

import com.ntt.authservice.auth.application.port.out.PermissionCache
import com.ntt.authservice.shared.cache.AbstractTwoTierCache
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Multi-tier permission cache: L1 (Caffeine) → L2 (Redis).
 *
 * Refactored (Layer R — R02): Delegates L1+L2 orchestration to [AbstractTwoTierCache].
 * Provides permission-specific serialization and composite key logic.
 *
 * This class manages TWO logical caches (permissions + roles) by using
 * two internal [AbstractTwoTierCache] instances with different key prefixes.
 */
@Component("multiTierPermissionCache")
class MultiTierPermissionCache(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.security.cache.permission.l1-max-size:10000}")
    private val l1MaxSize: Long,
    @Value("\${app.security.cache.permission.l1-ttl-seconds:30}")
    private val l1TtlSeconds: Long,
    @Value("\${app.security.cache.permission.l2-ttl-seconds:1800}")
    private val l2TtlSeconds: Long,
    @Value("\${app.security.cache.permission.l2-key-prefix:auth:perm:}")
    private val keyPrefix: String
) : PermissionCache {

    private val log = LoggerFactory.getLogger(MultiTierPermissionCache::class.java)

    /** Composite key for permission/role cache lookups. */
    private data class CacheKey(val userId: Long, val domainId: Long)

    /** Internal cache for permissions (List<String>). */
    private val permissionsCache = object : AbstractTwoTierCache<CacheKey, List<String>>(
        redisTemplate = redisTemplate,
        objectMapper = objectMapper,
        keyPrefix = "${keyPrefix}perms:",
        l1MaxSize = l1MaxSize,
        l1TtlSeconds = l1TtlSeconds,
        l2TtlSeconds = l2TtlSeconds
    ) {
        override fun toKeyString(key: CacheKey): String = "${key.userId}:${key.domainId}"

        override fun deserializeFromRedis(json: String): List<String>? {
            return try {
                objectMapper.readValue(json, object : TypeReference<List<String>>() {})
            } catch (ex: Exception) {
                log.warn("Failed to deserialize permissions from Redis: {}", ex.message)
                null
            }
        }
    }

    /** Internal cache for roles (List<String>). */
    private val rolesCache = object : AbstractTwoTierCache<CacheKey, List<String>>(
        redisTemplate = redisTemplate,
        objectMapper = objectMapper,
        keyPrefix = "${keyPrefix}roles:",
        l1MaxSize = l1MaxSize,
        l1TtlSeconds = l1TtlSeconds,
        l2TtlSeconds = l2TtlSeconds
    ) {
        override fun toKeyString(key: CacheKey): String = "${key.userId}:${key.domainId}"

        override fun deserializeFromRedis(json: String): List<String>? {
            return try {
                objectMapper.readValue(json, object : TypeReference<List<String>>() {})
            } catch (ex: Exception) {
                log.warn("Failed to deserialize roles from Redis: {}", ex.message)
                null
            }
        }
    }

    // --- PermissionCache interface implementation ---

    override fun getPermissions(userId: Long, domainId: Long): List<String>? {
        return permissionsCache.get(CacheKey(userId, domainId))
    }

    override fun putPermissions(userId: Long, domainId: Long, permissions: List<String>) {
        permissionsCache.put(CacheKey(userId, domainId), permissions)
    }

    override fun getRoles(userId: Long, domainId: Long): List<String>? {
        return rolesCache.get(CacheKey(userId, domainId))
    }

    override fun putRoles(userId: Long, domainId: Long, roles: List<String>) {
        rolesCache.put(CacheKey(userId, domainId), roles)
    }

    override fun invalidate(userId: Long, domainId: Long) {
        val key = CacheKey(userId, domainId)
        permissionsCache.invalidate(key)
        rolesCache.invalidate(key)
    }

    override fun invalidateAll() {
        permissionsCache.invalidateAll()
        rolesCache.invalidateAll()
    }
}
