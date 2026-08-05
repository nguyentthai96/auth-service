package com.ntt.authservice.rbac.adapter.out.persistence.mapper

import com.ntt.authservice.rbac.adapter.out.persistence.entity.PermissionEntities
import com.ntt.authservice.rbac.domain.model.Permission

/**
 * Manual mapping between Permission entities (JPA) ↔ Permission (domain).
 */

fun PermissionEntities.PermissionEntity.toDomain() = Permission(
    id = this.id ?: 0L,
    resourceId = this.resourceId,
    actionId = this.actionId,
    code = "${this.resourceId}:${this.actionId}", // computed code
    description = this.description ?: ""
)
