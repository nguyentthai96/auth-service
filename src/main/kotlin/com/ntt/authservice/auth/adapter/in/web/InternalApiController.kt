package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.ServiceTokenService
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Internal API controller — endpoints for inter-service communication (FR-021).
 * Protected by ServiceAuthFilter — requires service JWT.
 */
@RestController
@RequestMapping("/api/internal")
class InternalApiController(
    private val serviceTokenService: ServiceTokenService,
    private val userRepository: UserRepository,
    private val rbacEngine: RbacEngine
) {

    /**
     * Issue a service token (called by services at startup).
     */
    @PostMapping("/service-token")
    fun issueServiceToken(@Valid @RequestBody request: ServiceTokenRequest): ResponseEntity<Map<String, String>> {
        val token = serviceTokenService.generateServiceToken(request.serviceName)
        return ResponseEntity.ok(mapOf(
            "token" to token,
            "type" to "Bearer",
            "serviceName" to request.serviceName
        ))
    }

    /**
     * Get roles for a user by ID (called by other services for authorization).
     */
    @GetMapping("/users/{id}/roles")
    fun getUserRoles(@PathVariable id: Long, @RequestParam domainId: Long): ResponseEntity<Map<String, Any>> {
        val user = userRepository.findById(id).orElseThrow {
            ResourceNotFoundException("User", id)
        }
        val roles = rbacEngine.getUserRoles(id, domainId)
        return ResponseEntity.ok(mapOf(
            "userId" to id,
            "domainId" to domainId,
            "roles" to roles,
            "status" to user.status
        ))
    }
}

data class ServiceTokenRequest(
    val serviceName: String,
    val serviceSecret: String? = null
)
