package com.ntt.authservice.rbac.adapter.`in`.web

import com.ntt.authservice.rbac.adapter.out.persistence.entity.*
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.shared.exception.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

// ============================================================
// Domain Management Controller
// ============================================================
@RestController
@RequestMapping("/admin/domains")
class DomainController(
    private val domainRepository: DomainRepository,
    private val domainRoleRepository: DomainRoleRepository,
    private val domainResourceRepository: DomainResourceRepository,
    private val actionRepository: ActionRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository
) {

    @GetMapping
    fun listDomains(): ResponseEntity<List<DomainResponse>> {
        val domains = domainRepository.findAllByActiveTrue().map { it.toResponse() }
        return ResponseEntity.ok(domains)
    }

    @GetMapping("/{id}")
    fun getDomain(@PathVariable id: Long): ResponseEntity<DomainResponse> {
        val domain = domainRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Domain", id)
        }
        return ResponseEntity.ok(domain.toResponse())
    }

    @PostMapping
    @Transactional
    fun createDomain(@Valid @RequestBody request: DomainCreateRequest): ResponseEntity<DomainResponse> {
        if (domainRepository.existsByCode(request.code)) {
            throw DuplicateResourceException("Domain", "code", request.code)
        }

        val domain = DomainEntity().apply {
            code = request.code
            name = request.name
            description = request.description
        }
        val saved = domainRepository.save(domain)

        // Auto-create default roles (BR-006)
        val adminRole = DomainRoleEntity().apply {
            domainId = saved.id!!
            code = "DOMAIN_ADMIN"
            name = "Domain Admin"
            description = "Full access to all resources"
            hierarchyLevel = 0
            isDefault = true
        }
        val viewerRole = DomainRoleEntity().apply {
            domainId = saved.id!!
            code = "VIEWER"
            name = "Viewer"
            description = "Read-only access"
            hierarchyLevel = 99
            isDefault = true
        }
        domainRoleRepository.saveAll(listOf(adminRole, viewerRole))

        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    @PutMapping("/{id}")
    @Transactional
    fun updateDomain(
        @PathVariable id: Long,
        @Valid @RequestBody request: DomainUpdateRequest
    ): ResponseEntity<DomainResponse> {
        val domain = domainRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Domain", id)
        }
        domain.name = request.name ?: domain.name
        domain.description = request.description ?: domain.description
        domain.status = request.status ?: domain.status
        // updatedAt is auto-managed by AuditableEntity

        return ResponseEntity.ok(domainRepository.save(domain).toResponse())
    }
}

// ============================================================
// Role Management Controller
// ============================================================
@RestController
@RequestMapping("/admin/domains/{domainId}/roles")
class RoleController(
    private val domainRoleRepository: DomainRoleRepository,
    private val domainRepository: DomainRepository
) {

    @GetMapping
    fun listRoles(@PathVariable domainId: Long): ResponseEntity<List<RoleResponse>> {
        domainRepository.findById(domainId).orElseThrow { ResourceNotFoundException("Domain", domainId) }
        val roles = domainRoleRepository.findAllByDomainIdAndActiveTrue(domainId).map { it.toResponse() }
        return ResponseEntity.ok(roles)
    }

    @PostMapping
    @Transactional
    fun createRole(
        @PathVariable domainId: Long,
        @Valid @RequestBody request: RoleCreateRequest
    ): ResponseEntity<RoleResponse> {
        domainRepository.findById(domainId).orElseThrow { ResourceNotFoundException("Domain", domainId) }

        val existing = domainRoleRepository.findByDomainIdAndCodeAndActiveTrue(domainId, request.code)
        if (existing != null) {
            throw DuplicateResourceException("Role", "code", request.code)
        }

        val role = DomainRoleEntity().apply {
            this.domainId = domainId
            code = request.code
            name = request.name
            description = request.description
            hierarchyLevel = request.hierarchyLevel
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(domainRoleRepository.save(role).toResponse())
    }
}

// ============================================================
// Group Management Controller
// ============================================================
@RestController
@RequestMapping("/admin/domains/{domainId}/groups")
class GroupController(
    private val groupRepository: GroupRepository,
    private val groupRoleRepository: GroupRoleRepository,
    private val userGroupRepository: UserGroupRepository,
    private val domainRepository: DomainRepository
) {

    @GetMapping
    fun listGroups(@PathVariable domainId: Long): ResponseEntity<List<GroupResponse>> {
        domainRepository.findById(domainId).orElseThrow { ResourceNotFoundException("Domain", domainId) }
        val groups = groupRepository.findAllByDomainIdAndActiveTrue(domainId).map { it.toResponse() }
        return ResponseEntity.ok(groups)
    }

    @PostMapping
    @Transactional
    fun createGroup(
        @PathVariable domainId: Long,
        @Valid @RequestBody request: GroupCreateRequest
    ): ResponseEntity<GroupResponse> {
        domainRepository.findById(domainId).orElseThrow { ResourceNotFoundException("Domain", domainId) }

        val group = GroupEntity().apply {
            this.domainId = domainId
            name = request.name
            description = request.description
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(groupRepository.save(group).toResponse())
    }

    @PostMapping("/{groupId}/roles")
    @Transactional
    fun assignRole(
        @PathVariable domainId: Long,
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
        @PathVariable domainId: Long,
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
// Resource & Permission Management Controller
// ============================================================
@RestController
@RequestMapping("/admin/domains/{domainId}/resources")
class ResourceController(
    private val domainResourceRepository: DomainResourceRepository,
    private val domainRepository: DomainRepository
) {

    @GetMapping
    fun listResources(@PathVariable domainId: Long): ResponseEntity<List<ResourceResponse>> {
        domainRepository.findById(domainId).orElseThrow { ResourceNotFoundException("Domain", domainId) }
        val resources = domainResourceRepository.findAllByDomainIdAndActiveTrue(domainId).map { it.toResponse() }
        return ResponseEntity.ok(resources)
    }

    @PostMapping
    @Transactional
    fun createResource(
        @PathVariable domainId: Long,
        @Valid @RequestBody request: ResourceCreateRequest
    ): ResponseEntity<ResourceResponse> {
        domainRepository.findById(domainId).orElseThrow { ResourceNotFoundException("Domain", domainId) }

        val resource = DomainResourceEntity().apply {
            this.domainId = domainId
            code = request.code
            name = request.name
            description = request.description
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(domainResourceRepository.save(resource).toResponse())
    }
}

// ============================================================
// Permission Check Controller
// ============================================================
@RestController
@RequestMapping("/auth/permissions")
class PermissionCheckController(
    private val rbacEngine: RbacEngine
) {

    @PostMapping("/check")
    fun checkPermission(@Valid @RequestBody request: PermissionCheckRequest): ResponseEntity<PermissionCheckResponse> {
        val allowed = rbacEngine.hasPermission(
            userId = request.userId,
            domainId = request.domainId,
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
                domainId = request.domainId,
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

data class DomainCreateRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null
)

data class DomainUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val status: String? = null
)

data class DomainResponse(val id: Long, val code: String, val name: String, val description: String?, val status: String)
fun DomainEntity.toResponse() = DomainResponse(id!!, code, name, description, status)

data class RoleCreateRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null,
    val hierarchyLevel: Int = 99
)

data class RoleResponse(val id: Long, val code: String, val name: String, val description: String?, val hierarchyLevel: Int)
fun DomainRoleEntity.toResponse() = RoleResponse(id!!, code, name, description, hierarchyLevel)

data class GroupCreateRequest(
    @field:NotBlank val name: String,
    val description: String? = null
)

data class GroupResponse(val id: Long, val name: String, val description: String?, val domainId: Long)
fun GroupEntity.toResponse() = GroupResponse(id!!, name, description, domainId)

data class ResourceCreateRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null
)

data class ResourceResponse(val id: Long, val code: String, val name: String, val description: String?)
fun DomainResourceEntity.toResponse() = ResourceResponse(id!!, code, name, description)

data class AssignRoleRequest(val roleId: Long)
data class AddUserToGroupRequest(val userId: Long)

data class PermissionCheckRequest(
    val userId: Long,
    val domainId: Long,
    val resourceCode: String,
    val actionCode: String
)

data class BatchPermissionCheckRequest(
    val userId: Long,
    val domainId: Long,
    val checks: List<ResourceActionPair>
)

data class ResourceActionPair(val resourceCode: String, val actionCode: String)

data class PermissionCheckResponse(
    val allowed: Boolean,
    val userId: Long,
    val resource: String,
    val action: String
)
