package com.ntt.authservice.rbac.adapter.`in`.web

import com.ntt.authservice.rbac.adapter.out.persistence.entity.*
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.shared.exception.*
import com.ntt.authservice.shared.web.AdminController
import com.ntt.authservice.shared.web.BaseController
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

// ============================================================
// Role Management Controller (global scope)
// ============================================================
@RestController
@RequestMapping("/admin/roles")
class RoleController(
    private val roleRepository: RoleRepository
) : AdminController() {

    @GetMapping
    fun listRoles(): ResponseEntity<List<RoleResponse>> {
        val roles = roleRepository.findAllByActiveTrue().map { it.toResponse() }
        return ResponseEntity.ok(roles)
    }

    @PostMapping
    @Transactional
    fun createRole(
        @Valid @RequestBody request: RoleCreateRequest
    ): ResponseEntity<RoleResponse> {
        val existing = roleRepository.findByCodeAndActiveTrue(request.code)
        if (existing != null) {
            throw DuplicateResourceException("Role", "code", request.code)
        }

        val role = RoleEntity().apply {
            code = request.code
            name = request.name
            description = request.description
            hierarchyLevel = request.hierarchyLevel
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(roleRepository.save(role).toResponse())
    }
}

// ============================================================
// Group Management Controller (global scope)
// ============================================================
@RestController
@RequestMapping("/admin/groups")
class GroupController(
    private val groupRepository: GroupRepository,
    private val groupRoleRepository: GroupRoleRepository,
    private val userGroupRepository: UserGroupRepository
) : AdminController() {

    @GetMapping
    fun listGroups(): ResponseEntity<List<GroupResponse>> {
        val groups = groupRepository.findAllByActiveTrue().map { it.toResponse() }
        return ResponseEntity.ok(groups)
    }

    @PostMapping
    @Transactional
    fun createGroup(
        @Valid @RequestBody request: GroupCreateRequest
    ): ResponseEntity<GroupResponse> {
        val group = GroupEntity().apply {
            name = request.name
            description = request.description
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(groupRepository.save(group).toResponse())
    }

    @PostMapping("/{groupId}/roles")
    @Transactional
    fun assignRole(
        @PathVariable groupId: Long,
        @Valid @RequestBody request: AssignRoleRequest
    ): ResponseEntity<Void> {
        val groupRole = GroupRoleEntity().apply {
            this.groupId = groupId
            this.roleId = request.roleId
        }
        groupRoleRepository.save(groupRole)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/{groupId}/users")
    @Transactional
    fun addUserToGroup(
        @PathVariable groupId: Long,
        @Valid @RequestBody request: AddUserToGroupRequest
    ): ResponseEntity<Void> {
        val userGroup = UserGroupEntity().apply {
            userId = request.userId
            this.groupId = groupId
        }
        userGroupRepository.save(userGroup)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}

// ============================================================
// Resource & Permission Management Controller (global scope)
// ============================================================
@RestController
@RequestMapping("/admin/resources")
class ResourceController(
    private val resourceRepository: ResourceRepository
) : AdminController() {

    @GetMapping
    fun listResources(): ResponseEntity<List<ResourceResponse>> {
        val resources = resourceRepository.findAllByActiveTrue().map { it.toResponse() }
        return ResponseEntity.ok(resources)
    }

    @PostMapping
    @Transactional
    fun createResource(
        @Valid @RequestBody request: ResourceCreateRequest
    ): ResponseEntity<ResourceResponse> {
        val resource = ResourceEntity().apply {
            code = request.code
            name = request.name
            description = request.description
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(resourceRepository.save(resource).toResponse())
    }
}

// ============================================================
// Permission Check Controller (global scope)
// ============================================================
@RestController
@RequestMapping("/auth/permissions")
class PermissionCheckController(
    private val rbacEngine: RbacEngine
) : BaseController() {

    @PostMapping("/check")
    fun checkPermission(@Valid @RequestBody request: PermissionCheckRequest): ResponseEntity<PermissionCheckResponse> {
        val allowed = rbacEngine.hasPermission(
            userId = request.userId,
            resourceCode = request.resourceCode,
            actionCode = request.actionCode
        )
        return ResponseEntity.ok(
            PermissionCheckResponse(
                allowed = allowed,
                userId = request.userId,
                resource = request.resourceCode,
                action = request.actionCode
            )
        )
    }

    @PostMapping("/check-batch")
    fun checkBatch(@Valid @RequestBody request: BatchPermissionCheckRequest): ResponseEntity<List<PermissionCheckResponse>> {
        val results = request.checks.map { check ->
            val allowed = rbacEngine.hasPermission(
                userId = request.userId,
                resourceCode = check.resourceCode,
                actionCode = check.actionCode
            )
            PermissionCheckResponse(
                allowed = allowed,
                userId = request.userId,
                resource = check.resourceCode,
                action = check.actionCode
            )
        }
        return ResponseEntity.ok(results)
    }
}

// ============================================================
// DTOs
// ============================================================

data class RoleCreateRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null,
    val hierarchyLevel: Int = 99
)

data class RoleResponse(val id: Long, val code: String, val name: String, val description: String?, val hierarchyLevel: Int)
fun RoleEntity.toResponse() = RoleResponse(id!!, code, name, description, hierarchyLevel)

data class GroupCreateRequest(
    @field:NotBlank val name: String,
    val description: String? = null
)

data class GroupResponse(val id: Long, val name: String, val description: String?)
fun GroupEntity.toResponse() = GroupResponse(id!!, name, description)

data class ResourceCreateRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null
)

data class ResourceResponse(val id: Long, val code: String, val name: String, val description: String?)
fun ResourceEntity.toResponse() = ResourceResponse(id!!, code, name, description)

data class AssignRoleRequest(val roleId: Long)
data class AddUserToGroupRequest(val userId: Long)

data class PermissionCheckRequest(
    val userId: Long,
    val resourceCode: String,
    val actionCode: String
)

data class BatchPermissionCheckRequest(
    val userId: Long,
    val checks: List<ResourceActionPair>
)

data class ResourceActionPair(val resourceCode: String, val actionCode: String)

data class PermissionCheckResponse(
    val allowed: Boolean,
    val userId: Long,
    val resource: String,
    val action: String
)
