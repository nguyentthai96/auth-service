package com.ntt.authservice.rbac.application

import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * RBAC Engine — Stage 1 authorization.
 * Resolves: User → Groups → Roles → Permissions hierarchy.
 */
@Service
class RbacEngine(
    private val userGroupRepository: UserGroupRepository,
    private val groupRoleRepository: GroupRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val domainResourceRepository: DomainResourceRepository,
    private val actionRepository: ActionRepository,
    private val domainRoleRepository: DomainRoleRepository
) {

    private val log = LoggerFactory.getLogger(RbacEngine::class.java)

    /**
     * Check if user has specific permission in a domain.
     */
    @Transactional(readOnly = true)
    fun hasPermission(userId: Long, domainId: Long, resourceCode: String, actionCode: String): Boolean {
        val resource = domainResourceRepository.findByDomainIdAndCodeAndActiveTrue(domainId, resourceCode)
            ?: return false

        val action = actionRepository.findByCode(actionCode)
            ?: return false

        val permission = permissionRepository.findByResourceIdAndActionId(resource.id!!, action.id!!)
            ?: return false

        // Resolve user permissions through group→role→permission chain
        val userPermissionIds = resolveUserPermissions(userId)
        return permission.id!! in userPermissionIds
    }

    /**
     * Get all role codes for a user in a domain.
     */
    @Transactional(readOnly = true)
    fun getUserRoles(userId: Long, domainId: Long): List<String> {
        val roleIds = resolveUserRoleIds(userId)
        return domainRoleRepository.findAllByDomainIdAndActiveTrue(domainId)
            .filter { it.id!! in roleIds }
            .map { it.code }
    }

    /**
     * Get all effective permissions as "resource:action" strings.
     */
    @Transactional(readOnly = true)
    fun getEffectivePermissions(userId: Long, domainId: Long): List<String> {
        val roleIds = resolveUserRoleIds(userId)
        val permissionIds = roleIds.flatMap { roleId ->
            rolePermissionRepository.findAllByRoleIdAndActiveTrue(roleId)
                .map { it.permissionId }
        }.toSet()

        return permissionIds.mapNotNull { permId ->
            val perm = permissionRepository.findById(permId).orElse(null) ?: return@mapNotNull null
            val resource = domainResourceRepository.findById(perm.resourceId).orElse(null) ?: return@mapNotNull null
            val action = actionRepository.findById(perm.actionId).orElse(null) ?: return@mapNotNull null
            "${resource.code}:${action.code}"
        }
    }

    private fun resolveUserRoleIds(userId: Long): Set<Long> {
        val groups = userGroupRepository.findAllByUserIdAndActiveTrue(userId)
        return groups.flatMap { userGroup ->
            groupRoleRepository.findAllByGroupIdAndActiveTrue(userGroup.groupId)
                .map { it.roleId }
        }.toSet()
    }

    private fun resolveUserPermissions(userId: Long): Set<Long> {
        val roleIds = resolveUserRoleIds(userId)
        return roleIds.flatMap { roleId ->
            rolePermissionRepository.findAllByRoleIdAndActiveTrue(roleId)
                .map { it.permissionId }
        }.toSet()
    }
}
