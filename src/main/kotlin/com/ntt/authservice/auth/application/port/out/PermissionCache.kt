package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for permission caching (multi-tier: L1 Caffeine + L2 Redis).
 */
interface PermissionCache {
    fun getPermissions(userId: Long, domainId: Long): List<String>?
    fun putPermissions(userId: Long, domainId: Long, permissions: List<String>)
    fun getRoles(userId: Long, domainId: Long): List<String>?
    fun putRoles(userId: Long, domainId: Long, roles: List<String>)
    fun invalidate(userId: Long, domainId: Long)
    fun invalidateAll()
}
