package com.ntt.authservice.rbac.application.query

import com.ntt.authservice.auth.application.port.out.PermissionCache
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * GetUserRolesHandler — replaces RbacEngine.getUserRoles().
 * Batch-loads roles with cache support.
 */
@Component
class GetUserRolesHandler(
    private val userGroupRepository: UserGroupRepository,
    private val groupRoleRepository: GroupRoleRepository,
    private val domainRoleRepository: DomainRoleRepository,
    private val permissionCache: PermissionCache
) : QueryHandler<GetUserRolesQuery, List<String>> {

    private val log = LoggerFactory.getLogger(GetUserRolesHandler::class.java)

    @Transactional(readOnly = true)
    override suspend fun handle(query: GetUserRolesQuery): List<String> {
        // Check cache first
        val cached = permissionCache.getRoles(query.userId, query.domainId)
        if (cached != null) {
            return cached
        }

        // Resolve: User → Groups → Roles
        val groups = userGroupRepository.findAllByUserIdAndActiveTrue(query.userId)
        val roleIds = groups.flatMap { userGroup ->
            groupRoleRepository.findAllByGroupIdAndActiveTrue(userGroup.groupId)
                .map { it.roleId }
        }.toSet()

        val domainRoles = domainRoleRepository.findAllByDomainIdAndActiveTrue(query.domainId)
        val roles = domainRoles
            .filter { it.id!! in roleIds }
            .map { it.code }

        // Populate cache
        permissionCache.putRoles(query.userId, query.domainId, roles)
        log.debug("Loaded {} roles for userId={} domainId={}", roles.size, query.userId, query.domainId)

        return roles
    }


}
