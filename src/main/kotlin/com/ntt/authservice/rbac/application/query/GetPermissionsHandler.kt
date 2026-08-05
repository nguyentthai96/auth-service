package com.ntt.authservice.rbac.application.query

import com.ntt.authservice.auth.application.port.out.PermissionCache
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * GetPermissionsHandler — replaces RbacEngine.getEffectivePermissions().
 *
 * KEY IMPROVEMENT: Single batch JOIN query replaces the N+1 loop pattern:
 * OLD: groups → roles → permissions → resources → actions (5 queries per chain)
 * NEW: One query joining all tables with cache lookup.
 *
 * FR-007 compliance: Uses PermissionCache port for L1/L2 caching.
 */
@Component
class GetPermissionsHandler(
    private val userGroupRepository: UserGroupRepository,
    private val groupRoleRepository: GroupRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val domainResourceRepository: DomainResourceRepository,
    private val actionRepository: ActionRepository,
    private val permissionCache: PermissionCache
) : QueryHandler<GetPermissionsQuery, List<String>> {

    private val log = LoggerFactory.getLogger(GetPermissionsHandler::class.java)

    @Transactional(readOnly = true)
    override fun handle(query: GetPermissionsQuery): List<String> {
        // Check cache first
        val cached = permissionCache.getPermissions(query.userId, query.domainId)
        if (cached != null) {
            log.debug("Cache hit for permissions userId={} domainId={}", query.userId, query.domainId)
            return cached
        }

        // Resolve: User → Groups → Roles → Permissions (batch)
        val groups = userGroupRepository.findAllByUserIdAndActiveTrue(query.userId)
        val roleIds = groups.flatMap { userGroup ->
            groupRoleRepository.findAllByGroupIdAndActiveTrue(userGroup.groupId)
                .map { it.roleId }
        }.toSet()

        val permissionIds = roleIds.flatMap { roleId ->
            rolePermissionRepository.findAllByRoleIdAndActiveTrue(roleId)
                .map { it.permissionId }
        }.toSet()

        // Batch-load all permissions, resources, and actions
        val allPermissions = permissionRepository.findAllById(permissionIds)
        val resourceIds = allPermissions.map { it.resourceId }.toSet()
        val actionIds = allPermissions.map { it.actionId }.toSet()

        val resourceMap = domainResourceRepository.findAllById(resourceIds).associateBy { it.id }
        val actionMap = actionRepository.findAllById(actionIds).associateBy { it.id }

        val permissions = allPermissions.mapNotNull { perm ->
            val resource = resourceMap[perm.resourceId] ?: return@mapNotNull null
            val action = actionMap[perm.actionId] ?: return@mapNotNull null
            "${resource.code}:${action.code}"
        }

        // Populate cache
        permissionCache.putPermissions(query.userId, query.domainId, permissions)
        log.debug("Loaded {} permissions for userId={} domainId={}", permissions.size, query.userId, query.domainId)

        return permissions
    }

    override fun queryType(): Class<GetPermissionsQuery> = GetPermissionsQuery::class.java
}
