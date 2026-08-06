package com.ntt.authservice.rbac.adapter.out.persistence.mapper

import com.ntt.authservice.rbac.adapter.out.persistence.entity.PermissionEntity
import com.ntt.authservice.rbac.domain.model.Permission

/**
 * Manual mapping between Permission entities (JPA) ↔ Permission (domain).
 * Note: resourceCode and actionCode require lookup from Resource/Action tables.
 * This mapper provides a simplified mapping using IDs as codes for now.
 */

fun PermissionEntity.toDomain() = Permission(
    id = this.id ?: 0L,
    resourceCode = this.resourceId.toString(),
    actionCode = this.actionId.toString()
)
