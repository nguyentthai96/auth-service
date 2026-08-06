package com.ntt.authservice.rbac.application.query

import com.ntt.authservice.rbac.adapter.out.persistence.repository.*

import com.ntt.eventsourcingutils.lib.cqrs.query.QueryHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * CheckPermissionHandler — replaces RbacEngine.hasPermission().
 * Uses RbacResolver for chain resolution.
 */
@Component
class CheckPermissionHandler(
    private val domainResourceRepository: DomainResourceRepository,
    private val actionRepository: ActionRepository,
    private val permissionRepository: PermissionRepository,
    private val rbacResolver: RbacResolver
) : QueryHandler<CheckPermissionQuery, Boolean> {

    @Transactional(readOnly = true)
    override fun handle(query: CheckPermissionQuery): Boolean {
        val resource = domainResourceRepository.findByDomainIdAndCodeAndActiveTrue(
            query.domainId, query.resourceCode
        ) ?: return false

        val action = actionRepository.findByCode(query.actionCode)
            ?: return false

        val permission = permissionRepository.findByResourceIdAndActionId(resource.id!!, action.id!!)
            ?: return false

        val userPermissionIds = rbacResolver.resolveUserPermissionIds(query.userId)
        return permission.id!! in userPermissionIds
    }

    override fun queryType(): Class<CheckPermissionQuery> = CheckPermissionQuery::class.java
}
