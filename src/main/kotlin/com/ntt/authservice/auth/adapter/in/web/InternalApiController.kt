package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.ServiceTokenResult
import com.ntt.authservice.auth.adapter.`in`.web.dto.UserRolesResult
import com.ntt.authservice.auth.application.ServiceTokenService
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import com.ntt.authservice.shared.web.BaseController
import com.ntt.basecore.domain.web.payload.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Internal API controller — endpoints for inter-service communication (FR-021).
 * Protected by ServiceAuthFilter — requires service JWT.
 */
@RestController
@RequestMapping("/internal")
class InternalApiController(
    private val serviceTokenService: ServiceTokenService,
    private val userRepository: UserRepository,
    private val rbacEngine: RbacEngine
) : BaseController() {

    /**
     * Issue a service token (called by services at startup).
     */
    @PostMapping("/service-token")
    fun issueServiceToken(@Valid @RequestBody request: ServiceTokenRequest): ResponseEntity<ApiResponse<ServiceTokenResult>> {
        val token = serviceTokenService.generateServiceToken(request.serviceName)
        return okResponse(
            ServiceTokenResult(
                token = token,
                serviceName = request.serviceName
            )
        )
    }

    /**
     * Get roles for a user by ID (called by other services for authorization).
     */
    @GetMapping("/users/{id}/roles")
    fun getUserRoles(@PathVariable id: Long, @RequestParam domainId: Long): ResponseEntity<ApiResponse<UserRolesResult>> {
        val user = userRepository.findById(id).orElseThrow {
            ResourceNotFoundException("User", id)
        }
        val roles = rbacEngine.getUserRoles(id, domainId)
        return okResponse(
            UserRolesResult(
                userId = id,
                domainId = domainId,
                roles = roles,
                status = user.status
            )
        )
    }
}

data class ServiceTokenRequest(
    val serviceName: String,
    val serviceSecret: String? = null
)

