package com.ntt.authservice.rbac.domain.model

/**
 * Permission domain model — immutable.
 * Represents an access control permission as "resource:action" pair.
 */
data class Permission(
    val id: Long,
    val resourceCode: String,
    val actionCode: String
) {
    /**
     * String representation for JWT claims and API responses.
     */
    fun toPermissionString(): String = "$resourceCode:$actionCode"
}
