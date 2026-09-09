package com.ntt.authservice.pbac.adapter.`in`.web

import com.ntt.authservice.pbac.adapter.out.persistence.entity.PolicyConditionEntity
import com.ntt.authservice.pbac.adapter.out.persistence.entity.PolicyEntity
import com.ntt.authservice.pbac.adapter.out.persistence.repository.PolicyJpaRepository
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/domains/{domainId}/policies")
class PolicyController(
    private val policyRepository: PolicyJpaRepository
) {

    @GetMapping
    fun listPolicies(@PathVariable domainId: Long): ResponseEntity<List<PolicyResponse>> {
        val policies = policyRepository.findAllByDomainIdAndStatusAndActiveTrue(domainId)
        return ResponseEntity.ok(policies.map { it.toResponse() })
    }

    @GetMapping("/{id}")
    fun getPolicy(
        @PathVariable domainId: Long,
        @PathVariable id: Long
    ): ResponseEntity<PolicyResponse> {
        val policy = policyRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Policy", id)
        }
        return ResponseEntity.ok(policy.toResponse())
    }

    @PostMapping
    @Transactional
    fun createPolicy(
        @PathVariable domainId: Long,
        @Valid @RequestBody request: PolicyCreateRequest
    ): ResponseEntity<PolicyResponse> {
        val policy = PolicyEntity().apply {
            this.domainId = domainId
            name = request.name
            description = request.description
            resourceId = request.resourceId
            actionId = request.actionId
            effect = request.effect
            priority = request.priority
            status = "DRAFT" // SM-04: new policies start as DRAFT
        }

        // Add conditions
        request.conditions.forEachIndexed { index, cond ->
            val condEntity = PolicyConditionEntity().apply {
                policyId = 0L // Will be set after parent save
                attributePath = cond.attributePath
                operator = cond.operator
                value = cond.value
                valueType = cond.valueType
                conditionOrder = index
            }
            policy.conditions.add(condEntity)
        }

        val saved = policyRepository.save(policy)
        // Fix condition policyId references
        saved.conditions.forEach { it.policyId = saved.id!! }
        policyRepository.save(saved)

        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    @PutMapping("/{id}/activate")
    @Transactional
    fun activatePolicy(
        @PathVariable domainId: Long,
        @PathVariable id: Long
    ): ResponseEntity<PolicyResponse> {
        val policy = policyRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Policy", id)
        }

        // SM-04 fix: Validate conditions before activation
        if (policy.conditions.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        policy.status = "ACTIVE"
        // updatedAt is auto-managed by AuditableEntity
        return ResponseEntity.ok(policyRepository.save(policy).toResponse())
    }

    @PutMapping("/{id}/deactivate")
    @Transactional
    fun deactivatePolicy(
        @PathVariable domainId: Long,
        @PathVariable id: Long
    ): ResponseEntity<PolicyResponse> {
        val policy = policyRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Policy", id)
        }
        policy.status = "INACTIVE"
        // updatedAt is auto-managed by AuditableEntity
        return ResponseEntity.ok(policyRepository.save(policy).toResponse())
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun deletePolicy(
        @PathVariable domainId: Long,
        @PathVariable id: Long
    ): ResponseEntity<Void> {
        val policy = policyRepository.findById(id).orElseThrow {
            ResourceNotFoundException("Policy", id)
        }
        policy.active = false
        policy.status = "DELETED"
        // updatedAt is auto-managed by AuditableEntity
        policyRepository.save(policy)
        return ResponseEntity.noContent().build()
    }
}

// DTOs
data class PolicyCreateRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val resourceId: Long? = null,
    val actionId: Long? = null,
    val effect: String = "ALLOW",
    val priority: Int = 100,
    val conditions: List<ConditionDto> = emptyList()
)

data class ConditionDto(
    val attributePath: String,
    val operator: String,
    val value: String, // JSONB string
    val valueType: String = "STATIC"
)

data class PolicyResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val effect: String,
    val priority: Int,
    val status: String,
    val conditions: List<ConditionResponse>
)

data class ConditionResponse(
    val attributePath: String,
    val operator: String,
    val value: String,
    val valueType: String
)

fun PolicyEntity.toResponse() = PolicyResponse(
    id = id!!,
    name = name,
    description = description,
    effect = effect,
    priority = priority,
    status = status,
    conditions = conditions.map {
        ConditionResponse(it.attributePath, it.operator, it.value, it.valueType)
    }
)
