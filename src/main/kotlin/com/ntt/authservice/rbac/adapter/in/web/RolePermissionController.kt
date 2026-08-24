package com.ntt.authservice.rbac.adapter.`in`.web

import com.ntt.authservice.rbac.adapter.out.persistence.entity.*
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/**
 * Permission matrix management — assigns permissions (resource×action) to roles.
 */
@RestController
@RequestMapping("/api/domains/{domainId}/roles/{roleId}/permissions")
class RolePermissionController(
    private val rolePermissionRepository: RolePermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val domainResourceRepository: DomainResourceRepository,
    private val actionRepository: ActionRepository,
    private val domainRoleRepository: DomainRoleRepository
) {

    @GetMapping
    fun listRolePermissions(
        @PathVariable domainId: Long,
        @PathVariable roleId: Long
    ): ResponseEntity<List<RolePermissionResponse>> {
        val rolePerms = rolePermissionRepository.findAllByRoleIdAndActiveTrue(roleId)
        val result = rolePerms.mapNotNull { rp ->
            val perm = permissionRepository.findById(rp.permissionId).orElse(null)
            val resource = perm?.let { domainResourceRepository.findById(it.resourceId).orElse(null) }
            val action = perm?.let { actionRepository.findById(it.actionId).orElse(null) }
            if (resource != null && action != null) {
                RolePermissionResponse(
                    permissionId = rp.permissionId,
                    resourceCode = resource.code,
                    resourceName = resource.name,
                    actionCode = action.code,
                    actionName = action.name
                )
            } else null
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping
    @Transactional
    fun assignPermission(
        @PathVariable domainId: Long,
        @PathVariable roleId: Long,
        @Valid @RequestBody request: AssignPermissionRequest
    ): ResponseEntity<Void> {
        // Resolve resource
        val resource = domainResourceRepository.findByDomainIdAndCodeAndActiveTrue(domainId, request.resourceCode)
            ?: throw ResourceNotFoundException("Resource", request.resourceCode)

        // Resolve action
        val action = actionRepository.findByCode(request.actionCode)
            ?: throw ResourceNotFoundException("Action", request.actionCode)

        // Find or create permission entry
        val permission = permissionRepository.findByResourceIdAndActionId(resource.id!!, action.id!!)
            ?: permissionRepository.save(PermissionEntity().apply {
                resourceId = resource.id!!
                actionId = action.id!!
            })

        // Assign to role
        val rolePermission = RolePermissionEntity().apply {
            this.roleId = roleId
            this.permissionId = permission.id!!
        }
        rolePermissionRepository.save(rolePermission)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    /**
     * Bulk assign all actions to a role for a resource (shortcut for DOMAIN_ADMIN).
     */
    @PostMapping("/bulk")
    @Transactional
    fun assignAllPermissions(
        @PathVariable domainId: Long,
        @PathVariable roleId: Long,
        @Valid @RequestBody request: BulkAssignPermissionRequest
    ): ResponseEntity<Void> {
        val resource = domainResourceRepository.findByDomainIdAndCodeAndActiveTrue(domainId, request.resourceCode)
            ?: throw ResourceNotFoundException("Resource", request.resourceCode)

        val actions = if (request.actionCodes.isNullOrEmpty()) {
            actionRepository.findAll()
        } else {
            request.actionCodes.mapNotNull { actionRepository.findByCode(it) }
        }

        actions.forEach { action ->
            val permission = permissionRepository.findByResourceIdAndActionId(resource.id!!, action.id!!)
                ?: permissionRepository.save(PermissionEntity().apply {
                    resourceId = resource.id!!
                    actionId = action.id!!
                })

            val rolePermission = RolePermissionEntity().apply {
                this.roleId = roleId
                this.permissionId = permission.id!!
            }
            rolePermissionRepository.save(rolePermission)
        }

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}

data class AssignPermissionRequest(
    val resourceCode: String,
    val actionCode: String
)

data class BulkAssignPermissionRequest(
    val resourceCode: String,
    val actionCodes: List<String>? = null // null = all actions
)

data class RolePermissionResponse(
    val permissionId: Long,
    val resourceCode: String,
    val resourceName: String,
    val actionCode: String,
    val actionName: String
)
