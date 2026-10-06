package com.ntt.authservice.shared.security.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

/**
 * Kafka event model for menu permission changes.
 *
 * Published by system-admin-service when menu items are created, updated, or deleted.
 * Consumed by auth-service to sync endpoint security rules.
 *
 * Topic: menu.permission.changed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MenuPermissionChangedEvent(
    val eventType: String = "MENU_PERMISSION_CHANGED",
    val timestamp: Instant = Instant.now(),
    val correlationId: String? = null,
    val payload: MenuPermissionPayload
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MenuPermissionPayload(
    val action: String, // CREATED, UPDATED, DELETED
    val menuCode: String,
    val menuPath: String? = null,
    val menuType: String = "MENU",
    val domainId: Long = 0,
    val permissions: List<MenuPermissionInfo> = emptyList(),
    val assignedRoles: List<MenuRoleAssignment> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MenuPermissionInfo(
    val code: String,
    val name: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MenuRoleAssignment(
    val roleId: Long,
    val roleName: String = "",
    val permissions: List<String> = emptyList()
)
