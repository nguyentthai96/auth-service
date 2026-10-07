package com.ntt.auth.client.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.ntt.auth.client.repository.PermissionReadRepository
import com.ntt.auth.client.repository.RoleReadRepository
import com.ntt.auth.client.repository.UserReadRepository
import com.ntt.basebusiness.shared.IPermissionChecker
import com.ntt.basebusiness.shared.IUserReader
import com.ntt.basebusiness.shared.vo.UserBasicVO
import java.time.Duration

/**
 * JPA-backed user reader with Caffeine session cache.
 * TTL: 60s sliding (expireAfterAccess) — actively used entries stay warm.
 */
class JpaUserReader(
    private val repository: UserReadRepository
) : IUserReader {

    private val cacheById = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofSeconds(60))
        .build<Long, UserBasicVO?>()

    private val cacheByUsername = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofSeconds(60))
        .build<String, UserBasicVO?>()

    override fun getUserById(userId: Long): UserBasicVO? {
        return cacheById.get(userId) {
            repository.findById(userId)?.toVO()
        }
    }

    override fun getUserByUsername(username: String): UserBasicVO? {
        return cacheByUsername.get(username) {
            repository.findByUsername(username)?.toVO()
        }
    }

    override fun getUsersByIds(userIds: List<Long>): List<UserBasicVO> {
        return repository.findAllByIdIn(userIds).map { it.toVO() }
    }

    fun invalidateAll() {
        cacheById.invalidateAll()
        cacheByUsername.invalidateAll()
    }

    private fun com.ntt.auth.client.entity.UserReadModel.toVO() = UserBasicVO(
        id = id,
        username = username,
        email = email,
        fullName = fullName,
        phone = phone,
        avatarUrl = avatarUrl,
        status = status
    )
}

/**
 * JPA-backed permission checker with Caffeine session cache.
 * TTL: 60s sliding (expireAfterAccess).
 */
class JpaPermissionChecker(
    private val permissionRepository: PermissionReadRepository,
    private val roleRepository: RoleReadRepository
) : IPermissionChecker {

    private val permissionsCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofSeconds(60))
        .build<Long, Set<String>>()

    private val rolesCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofSeconds(60))
        .build<Long, Set<String>>()

    override fun hasPermission(userId: Long, permissionCode: String): Boolean {
        return getUserPermissions(userId).contains(permissionCode)
    }

    override fun getUserPermissions(userId: Long): Set<String> {
        return permissionsCache.get(userId) {
            permissionRepository.findPermissionCodesByUserId(userId)
        } ?: emptySet()
    }

    override fun getUserRoles(userId: Long): Set<String> {
        return rolesCache.get(userId) {
            roleRepository.findRolesByUserId(userId).map { it.code }.toSet()
        } ?: emptySet()
    }

    fun invalidateAll() {
        permissionsCache.invalidateAll()
        rolesCache.invalidateAll()
    }
}
