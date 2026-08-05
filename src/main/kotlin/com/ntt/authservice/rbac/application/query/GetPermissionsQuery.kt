package com.ntt.authservice.rbac.application.query

import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to get effective permissions for a user in a domain.
 * Returns batch-loaded permissions as "resource:action" strings.
 */
data class GetPermissionsQuery(
    val userId: Long,
    val domainId: Long
) : Query<List<String>>
