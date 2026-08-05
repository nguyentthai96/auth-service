package com.ntt.authservice.auth.adapter.out.cache

import com.ntt.authservice.auth.application.port.out.PermissionCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory permission cache adapter — Phase 1 implementation.
 *
 * Phase 4 will add:
 * - L1: Caffeine local cache (5min TTL)
 * - L2: Redis distributed cache (30min TTL)
 * - Event-driven invalidation via PermissionChangedEvent
 */
@Component
class InMemoryPermissionCache : PermissionCache {

    private val log = LoggerFactory.getLogger(InMemoryPermissionCache::class.java)

    private val permissionsCache = ConcurrentHashMap<String, List<String>>()
    private val rolesCache = ConcurrentHashMap<String, List<String>>()

    private fun key(userId: Long, domainId: Long) = "$userId:$domainId"

    override fun getPermissions(userId: Long, domainId: Long): List<String>? {
        return permissionsCache[key(userId, domainId)]
    }

    override fun putPermissions(userId: Long, domainId: Long, permissions: List<String>) {
        permissionsCache[key(userId, domainId)] = permissions
    }

    override fun getRoles(userId: Long, domainId: Long): List<String>? {
        return rolesCache[key(userId, domainId)]
    }

    override fun putRoles(userId: Long, domainId: Long, roles: List<String>) {
        rolesCache[key(userId, domainId)] = roles
    }

    override fun invalidate(userId: Long, domainId: Long) {
        val k = key(userId, domainId)
        permissionsCache.remove(k)
        rolesCache.remove(k)
        log.debug("Cache invalidated for userId={} domainId={}", userId, domainId)
    }

    override fun invalidateAll() {
        permissionsCache.clear()
        rolesCache.clear()
        log.info("All permission caches invalidated")
    }
}
