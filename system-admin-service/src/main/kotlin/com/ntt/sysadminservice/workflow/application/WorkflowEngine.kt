package com.ntt.sysadminservice.workflow.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowInstanceEntity
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowStepEntity
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Workflow engine — state machine for approval workflow (FR-013).
 * States: PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED
 */
@Component
class WorkflowEngine(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(WorkflowEngine::class.java)

    companion object {
        val VALID_TRANSITIONS = mapOf(
            "PENDING" to setOf("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS" to setOf("APPROVED", "REJECTED", "ESCALATED", "CANCELLED"),
            "ESCALATED" to setOf("APPROVED", "REJECTED", "CANCELLED")
        )

        val TERMINAL_STATES = setOf("APPROVED", "REJECTED", "CANCELLED")
    }

    /**
     * Process a decision on a workflow step.
     */
    @Transactional
    fun processDecision(
        instanceId: Long,
        stepId: Long,
        decision: String,
        userId: Long,
        comments: String?
    ): WorkflowInstanceEntity {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        if (instance.status in TERMINAL_STATES) {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Workflow already completed: ${instance.status}")
        }

        val step = entityManager.find(WorkflowStepEntity::class.java, stepId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow step not found: $stepId")

        if (step.status != "PENDING" && step.status != "ESCALATED") {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Step already processed: ${step.status}")
        }

        // Validate transition
        if (decision !in setOf("APPROVED", "REJECTED")) {
            throw SysAdminException(SysAdminErrorCode.INVALID_TRANSITION, "Invalid decision: $decision")
        }

        // Record decision
        step.status = decision
        step.decision = decision
        step.decidedAt = Instant.now()
        step.comments = comments
        entityManager.merge(step)

        // Advance workflow
        if (decision == "REJECTED") {
            instance.status = "REJECTED"
            instance.completedAt = Instant.now()
        } else {
            // Check if there are more steps
            val nextSteps = entityManager
                .createQuery("SELECT s FROM WorkflowStepEntity s WHERE s.instanceId = :instanceId AND s.stepOrder > :order AND s.status = 'PENDING' ORDER BY s.stepOrder", WorkflowStepEntity::class.java)
                .setParameter("instanceId", instanceId)
                .setParameter("order", step.stepOrder)
                .resultList

            if (nextSteps.isEmpty()) {
                instance.status = "APPROVED"
                instance.completedAt = Instant.now()
            } else {
                instance.currentStep = nextSteps.first().stepOrder
                instance.status = "IN_PROGRESS"
            }
        }

        entityManager.merge(instance)
        log.info("Workflow decision: instanceId={}, stepId={}, decision={}, newStatus={}", instanceId, stepId, decision, instance.status)
        return instance
    }

    /**
     * Escalate a pending step that has exceeded its timeout.
     */
    @Transactional
    fun escalateStep(step: WorkflowStepEntity) {
        step.status = "ESCALATED"
        step.escalated = true
        entityManager.merge(step)

        val instance = entityManager.find(WorkflowInstanceEntity::class.java, step.instanceId)
        if (instance != null) {
            instance.status = "ESCALATED"
            entityManager.merge(instance)
        }

        log.info("Workflow step escalated: stepId={}, instanceId={}", step.id, step.instanceId)
    }
}
