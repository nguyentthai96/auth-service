package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.AccountLifecycleService
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Account Lifecycle API (FR-009) — GDPR compliance.
 *
 * Endpoints for end users to:
 * - Deactivate their account
 * - Request deletion (GDPR Right to Erasure)
 * - Cancel deletion request
 * - Request data export (GDPR Right to Data Portability)
 * - Check export status
 */
@RestController
@RequestMapping("/api/account")
class AccountLifecycleController(
    private val accountLifecycleService: AccountLifecycleService
) {

    /**
     * Deactivate own account — user-initiated.
     */
    @PostMapping("/deactivate")
    fun deactivateAccount(): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()
        accountLifecycleService.deactivateAccount(userId)
        return ResponseEntity.ok(mapOf(
            "message" to "Account deactivated. Contact admin to reactivate."
        ))
    }

    /**
     * Request account deletion — GDPR Right to Erasure.
     * 30-day grace period before permanent deletion.
     */
    @PostMapping("/deletion-request")
    fun requestDeletion(
        @Valid @RequestBody request: DeletionRequest?
    ): ResponseEntity<Map<String, Any>> {
        val userId = getCurrentUserId()
        val result = accountLifecycleService.requestDeletion(userId, request?.reason)
        return ResponseEntity.ok(mapOf(
            "message" to "Deletion request created. Account will be permanently deleted after grace period.",
            "requestId" to (result.id ?: 0),
            "scheduledDeleteAt" to result.scheduledDeleteAt.toString(),
            "gracePeriodDays" to AccountLifecycleService.DELETION_GRACE_PERIOD_DAYS
        ))
    }

    /**
     * Cancel a pending deletion request.
     */
    @DeleteMapping("/deletion-request")
    fun cancelDeletion(): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()
        accountLifecycleService.cancelDeletion(userId)
        return ResponseEntity.ok(mapOf(
            "message" to "Deletion request cancelled. Account restored to active."
        ))
    }

    /**
     * Request data export — GDPR Right to Data Portability.
     */
    @PostMapping("/export")
    fun requestDataExport(): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        val export = accountLifecycleService.requestDataExport(userId)
        return ResponseEntity.ok(mapOf(
            "exportId" to export.id,
            "status" to export.status,
            "requestedAt" to export.requestedAt.toString(),
            "expiresAt" to export.expiresAt?.toString()
        ))
    }

    /**
     * Get data export status.
     */
    @GetMapping("/export/{exportId}")
    fun getExportStatus(@PathVariable exportId: Long): ResponseEntity<Map<String, Any?>> {
        val export = accountLifecycleService.getDataExport(exportId)
        return ResponseEntity.ok(mapOf(
            "exportId" to export.id,
            "status" to export.status,
            "fileSizeBytes" to export.fileSizeBytes,
            "completedAt" to export.completedAt?.toString(),
            "expiresAt" to export.expiresAt?.toString()
        ))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw InvalidCredentialsException()
    }
}

data class DeletionRequest(
    @field:Size(max = 1000, message = "Reason must be under 1000 characters")
    val reason: String? = null
)
