package com.ntt.authservice.rbac.application.query

import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to get user roles in a domain.
 */
data class GetUserRolesQuery(
    val userId: Long,
    val domainId: Long
) : Query<List<String>>
