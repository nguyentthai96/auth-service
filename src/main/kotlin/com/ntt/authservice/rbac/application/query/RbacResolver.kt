package com.ntt.authservice.rbac.application.query

import com.ntt.authservice.rbac.adapter.out.persistence.repository.GroupRoleRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.RolePermissionRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserGroupRepository
import org.springframework.stereotype.Component

/**
 * RBAC resolver — extracted from RbacEngine.resolveUserRoleIds() + resolveUserPermissions().
 * Resolves the role/permission chain: User → Groups → Roles → Permissions.
 */
@Component
class RbacResolver(
    private val userGroupRepository: UserGroupRepository,
    private val groupRoleRepository: GroupRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository
) {

    /**
     * Resolve all role IDs for a user through their group memberships.
     * Path: User → UserGroup → GroupRole → roleId
     */
    fun resolveUserRoleIds(userId: Long): Set<Long> {
        val groups = userGroupRepository.findAllByUserIdAndActiveTrue(userId)
        return groups.flatMap { userGroup ->
            groupRoleRepository.findAllByGroupIdAndActiveTrue(userGroup.groupId)
                .map { it.roleId }
        }.toSet()
    }

    /**
     * Resolve all permission IDs for a user through their roles.
     * Path: User → Groups → Roles → RolePermission → permissionId
     */
    fun resolveUserPermissionIds(userId: Long): Set<Long> {
        val roleIds = resolveUserRoleIds(userId)
        return roleIds.flatMap { roleId ->
            rolePermissionRepository.findAllByRoleIdAndActiveTrue(roleId)
                .map { it.permissionId }
        }.toSet()
    }
}
