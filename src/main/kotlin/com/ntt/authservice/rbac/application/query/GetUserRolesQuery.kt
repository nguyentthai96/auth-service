package com.ntt.authservice.rbac.application.query

import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to get user roles (global scope — domain_id removed).
 */
data class GetUserRolesQuery(
    val userId: Long
) : Query<List<String>>
