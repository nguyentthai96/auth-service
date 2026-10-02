package com.ntt.authservice.rbac.application.query

import com.ntt.eventsourcingutils.lib.cqrs.query.Query

/**
 * Query to check if a user has a specific permission (global scope — domain_id removed).
 */
data class CheckPermissionQuery(
    val userId: Long,
    val resourceCode: String,
    val actionCode: String
) : Query<Boolean>
