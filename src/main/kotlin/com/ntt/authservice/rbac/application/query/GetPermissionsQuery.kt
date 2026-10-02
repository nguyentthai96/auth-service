package com.ntt.authservice.rbac.application.query

import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to get effective permissions for a user (global scope — domain_id removed).
 * Returns batch-loaded permissions as "resource:action" strings.
 */
data class GetPermissionsQuery(
    val userId: Long
) : Query<List<String>>
