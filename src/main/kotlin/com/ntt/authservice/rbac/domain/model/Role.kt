package com.ntt.authservice.rbac.domain.model

/**
 * Role domain model — immutable.
 * A role aggregates a set of permissions within a domain.
 */
data class Role(
    val id: Long,
    val code: String,
    val name: String? = null,
    val permissions: List<Permission> = emptyList()
)
