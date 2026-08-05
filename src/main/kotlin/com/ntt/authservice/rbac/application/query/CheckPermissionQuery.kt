package com.ntt.authservice.rbac.application.query

import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to check if a user has a specific permission in a domain.
 */
data class CheckPermissionQuery(
    val userId: Long,
    val domainId: Long,
    val resourceCode: String,
    val actionCode: String
) : Query<Boolean>
