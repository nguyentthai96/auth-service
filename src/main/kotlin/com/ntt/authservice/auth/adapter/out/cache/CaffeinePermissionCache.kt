package com.ntt.authservice.auth.adapter.out.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.ntt.authservice.auth.application.port.out.PermissionCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Caffeine-based L1 permission cache — replaces InMemoryPermissionCache.
 *
 * Configuration:
 * - TTL: app.security.cache.permission.l1-ttl-seconds (default 30s)
 * - Max size: app.security.cache.permission.l1-max-size (default 10000)
 *
 * Phase 4 TODO: Wrap with Redis L2 in MultiTierPermissionCache.
 */
@Component
@Primary
class CaffeinePermissionCache(
    @Value("\${app.security.cache.permission.l1-ttl-seconds:30}")
    ttlSeconds: Long,
    @Value("\${app.security.cache.permission.l1-max-size:10000}")
    maxSize: Long
) : PermissionCache {

    private val log = LoggerFactory.getLogger(CaffeinePermissionCache::class.java)

    private val permissionsCache = Caffeine.newBuilder()
        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
        .maximumSize(maxSize)
        .recordStats()
        .build<String, List<String>>()

    private val rolesCache = Caffeine.newBuilder()
        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
        .maximumSize(maxSize)
        .recordStats()
        .build<String, List<String>>()

    private fun key(userId: Long, domainId: Long) = "$userId:$domainId"

    override fun getPermissions(userId: Long, domainId: Long): List<String>? {
        return permissionsCache.getIfPresent(key(userId, domainId))
    }

    override fun putPermissions(userId: Long, domainId: Long, permissions: List<String>) {
        permissionsCache.put(key(userId, domainId), permissions)
    }

    override fun getRoles(userId: Long, domainId: Long): List<String>? {
        return rolesCache.getIfPresent(key(userId, domainId))
    }

    override fun putRoles(userId: Long, domainId: Long, roles: List<String>) {
        rolesCache.put(key(userId, domainId), roles)
    }

    override fun invalidate(userId: Long, domainId: Long) {
        val k = key(userId, domainId)
        permissionsCache.invalidate(k)
        rolesCache.invalidate(k)
        log.debug("Caffeine cache invalidated for userId={} domainId={}", userId, domainId)
    }

    override fun invalidateAll() {
        permissionsCache.invalidateAll()
        rolesCache.invalidateAll()
        log.info("All Caffeine permission caches invalidated")
    }
}
