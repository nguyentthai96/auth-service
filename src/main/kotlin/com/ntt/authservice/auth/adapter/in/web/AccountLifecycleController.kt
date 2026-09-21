package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.DataExportResult
import com.ntt.authservice.auth.adapter.`in`.web.dto.DeletionRequestResult
import com.ntt.authservice.auth.application.AccountLifecycleService
import com.ntt.authservice.shared.web.AuthenticatedController
import com.ntt.basecore.domain.web.payload.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
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
 *
 * FR-005: Success response i18n — messages resolved via MessageSource.
 */
@RestController
@RequestMapping("/account")
class AccountLifecycleController(
    private val accountLifecycleService: AccountLifecycleService
) : AuthenticatedController() {

    /**
     * Deactivate own account — user-initiated.
     */
    @PostMapping("/deactivate")
    fun deactivateAccount(): ResponseEntity<ApiResponse<Unit>> {
        accountLifecycleService.deactivateAccount(currentUserId())
        return okMessageResponse("auth.account_deactivated")
    }

    /**
     * Request account deletion — GDPR Right to Erasure.
     * 30-day grace period before permanent deletion.
     */
    @PostMapping("/deletion-request")
    fun requestDeletion(
        @Valid @RequestBody request: DeletionRequest?
    ): ResponseEntity<ApiResponse<DeletionRequestResult>> {
        val result = accountLifecycleService.requestDeletion(currentUserId(), request?.reason)
        return okResponse(
            DeletionRequestResult(
                requestId = result.id ?: 0,
                scheduledDeleteAt = result.scheduledDeleteAt.toString(),
                gracePeriodDays = AccountLifecycleService.DELETION_GRACE_PERIOD_DAYS
            ),
            "auth.deletion_requested"
        )
    }

    /**
     * Cancel a pending deletion request.
     */
    @DeleteMapping("/deletion-request")
    fun cancelDeletion(): ResponseEntity<ApiResponse<Unit>> {
        accountLifecycleService.cancelDeletion(currentUserId())
        return okMessageResponse("auth.deletion_cancelled")
    }

    /**
     * Request data export — GDPR Right to Data Portability.
     */
    @PostMapping("/export")
    fun requestDataExport(): ResponseEntity<ApiResponse<DataExportResult>> {
        val export = accountLifecycleService.requestDataExport(currentUserId())
        return okResponse(
            DataExportResult(
                exportId = export.id,
                status = export.status,
                requestedAt = export.requestedAt.toString(),
                expiresAt = export.expiresAt?.toString()
            )
        )
    }

    /**
     * Get data export status.
     */
    @GetMapping("/export/{exportId}")
    fun getExportStatus(@PathVariable exportId: Long): ResponseEntity<ApiResponse<DataExportResult>> {
        val export = accountLifecycleService.getDataExport(exportId)
        return okResponse(
            DataExportResult(
                exportId = export.id,
                status = export.status,
                requestedAt = export.requestedAt.toString(),
                expiresAt = export.expiresAt?.toString(),
                fileSizeBytes = export.fileSizeBytes
            )
        )
    }
}

data class DeletionRequest(
    @field:Size(max = 1000, message = "Reason must be under 1000 characters")
    val reason: String? = null
)

