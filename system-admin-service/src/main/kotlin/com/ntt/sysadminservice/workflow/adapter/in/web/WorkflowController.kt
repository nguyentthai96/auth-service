package com.ntt.sysadminservice.workflow.adapter.`in`.web

import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowInstanceEntity
import com.ntt.sysadminservice.workflow.application.WorkflowService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Workflow controller — REST endpoints for approval workflow (FR-013).
 */
@RestController
@RequestMapping("/api/admin/workflows")
class WorkflowController(
    private val workflowService: WorkflowService
) {

    @PostMapping("/submit")
    fun submitForApproval(@Valid @RequestBody request: SubmitWorkflowRequest): ResponseEntity<WorkflowInstanceEntity> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(workflowService.submitForApproval(
            request.workflowCode, request.domainId, request.entityType, request.entityId, userId
        ))
    }

    @GetMapping("/{id}")
    fun getWorkflow(@PathVariable id: Long): ResponseEntity<WorkflowInstanceEntity> {
        return ResponseEntity.ok(workflowService.getWorkflowInstance(id))
    }

    @PostMapping("/{instanceId}/steps/{stepId}/decide")
    fun processDecision(
        @PathVariable instanceId: Long,
        @PathVariable stepId: Long,
        @Valid @RequestBody request: DecisionRequest
    ): ResponseEntity<WorkflowInstanceEntity> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(workflowService.processDecision(instanceId, stepId, request.decision, userId, request.comments))
    }

    @PostMapping("/{id}/cancel")
    fun cancelWorkflow(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        val userId = getCurrentUserId()
        workflowService.cancelWorkflow(id, userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong() ?: 0
    }
}

data class SubmitWorkflowRequest(
    @field:NotBlank val workflowCode: String,
    val domainId: Long,
    @field:NotBlank val entityType: String,
    @field:NotBlank val entityId: String
)

data class DecisionRequest(
    @field:NotBlank val decision: String,
    val comments: String? = null
)
