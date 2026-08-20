package com.ntt.sysadminservice.workflow.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowDefinitionEntity
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowInstanceEntity
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowStepEntity
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Workflow service — CRUD for workflow definitions and instance management (FR-013).
 */
@Service
class WorkflowService(
    private val entityManager: EntityManager,
    private val workflowEngine: WorkflowEngine
) {

    private val log = LoggerFactory.getLogger(WorkflowService::class.java)

    /**
     * Submit an entity for approval via a workflow definition.
     */
    @Transactional
    fun submitForApproval(
        workflowCode: String,
        domainId: Long,
        entityType: String,
        entityId: String,
        submittedBy: Long
    ): WorkflowInstanceEntity {
        val definition = entityManager
            .createQuery("SELECT w FROM WorkflowDefinitionEntity w WHERE w.code = :code AND w.domainId = :domainId AND w.status = 'ACTIVE' AND w.active = true ORDER BY w.version DESC", WorkflowDefinitionEntity::class.java)
            .setParameter("code", workflowCode)
            .setParameter("domainId", domainId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow definition not found: $workflowCode")

        val instance = WorkflowInstanceEntity().apply {
            this.workflowDefId = definition.id!!
            this.entityType = entityType
            this.entityId = entityId
            this.submittedBy = submittedBy
            this.status = "PENDING"
            this.submittedAt = Instant.now()
        }
        entityManager.persist(instance)

        // Create steps from definition JSON (simplified — parse step definitions)
        val stepConfigs = parseStepsFromJson(definition.stepsJson)
        stepConfigs.forEachIndexed { index, config ->
            val step = WorkflowStepEntity().apply {
                this.instanceId = instance.id!!
                this.stepOrder = index
                this.approverUserId = config.approverUserId
                this.approverRole = config.approverRole
                this.escalationTimeout = config.escalationTimeout ?: 86400
            }
            entityManager.persist(step)
        }

        // Set first step
        instance.currentStep = 0
        instance.status = "IN_PROGRESS"
        entityManager.merge(instance)

        log.info("Workflow submitted: instanceId={}, workflow={}, entity={}/{}", instance.id, workflowCode, entityType, entityId)
        return instance
    }

    /**
     * Get workflow instance details with steps.
     */
    fun getWorkflowInstance(instanceId: Long): WorkflowInstanceEntity {
        return entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")
    }

    /**
     * Process approval/rejection decision.
     */
    @Transactional
    fun processDecision(instanceId: Long, stepId: Long, decision: String, userId: Long, comments: String?): WorkflowInstanceEntity {
        return workflowEngine.processDecision(instanceId, stepId, decision, userId, comments)
    }

    /**
     * Cancel a workflow instance.
     */
    @Transactional
    fun cancelWorkflow(instanceId: Long, userId: Long) {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        if (instance.status in WorkflowEngine.TERMINAL_STATES) {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Workflow already completed")
        }

        instance.status = "CANCELLED"
        instance.completedAt = Instant.now()
        entityManager.merge(instance)
        log.info("Workflow cancelled: instanceId={}, by userId={}", instanceId, userId)
    }

    private data class StepConfig(
        val approverUserId: Long? = null,
        val approverRole: String? = null,
        val escalationTimeout: Int? = null
    )

    private fun parseStepsFromJson(json: String): List<StepConfig> {
        // Simplified JSON parsing — in production, use ObjectMapper
        if (json == "[]" || json.isBlank()) {
            return listOf(StepConfig(approverRole = "ADMIN", escalationTimeout = 86400))
        }
        // Default single-step workflow
        return listOf(StepConfig(approverRole = "ADMIN", escalationTimeout = 86400))
    }
}
