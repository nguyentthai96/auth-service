package com.ntt.authservice.auth.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.adapter.out.persistence.entity.AccountDataExportEntity
import com.ntt.authservice.auth.adapter.out.persistence.entity.AccountDeletionRequestEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.AccountDataExportRepository
import com.ntt.authservice.auth.adapter.out.persistence.repository.AccountDeletionRequestRepository
import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserDomainRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.auth.application.port.out.AccountDeactivatedEvent
import com.ntt.authservice.auth.application.port.out.AccountDeletedEvent
import com.ntt.authservice.auth.application.port.out.EventPublisher
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Account Lifecycle Service (FR-009) — GDPR compliance.
 *
 * Supports:
 * - Deactivate account (user-initiated or admin)
 * - Request deletion (GDPR Right to Erasure — 30 day grace period)
 * - Cancel deletion within grace period
 * - Export user data (GDPR Right to Data Portability)
 * - Scheduled processing of expired deletion requests
 */
@Service
class AccountLifecycleService(
    private val userRepository: UserRepository,
    private val userDomainRepository: UserDomainRepository,
    private val loginSessionRepository: LoginSessionRepository,
    private val deletionRequestRepository: AccountDeletionRequestRepository,
    private val dataExportRepository: AccountDataExportRepository,
    private val loginSessionService: LoginSessionService,
    private val rbacEngine: RbacEngine,
    private val auditLogService: AuditLogService,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: EventPublisher
) {

    private val log = LoggerFactory.getLogger(AccountLifecycleService::class.java)

    companion object {
        /** GDPR grace period before permanent deletion (days). */
        const val DELETION_GRACE_PERIOD_DAYS = 30L
        /** Data export download link validity (days). */
        const val EXPORT_EXPIRY_DAYS = 7L
    }

    /**
     * Deactivate an account — revoke all sessions, set status to DEACTIVATED.
     * User can reactivate later by contacting admin.
     */
    @Transactional
    fun deactivateAccount(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        user.status = "DEACTIVATED"
        userRepository.save(user)

        // Revoke all active sessions
        loginSessionService.revokeAllSessions(userId, "ACCOUNT_DEACTIVATED")

        // Publish cross-service deactivation event (FR-009 GDPR enhancement)
        eventPublisher.publish(AccountDeactivatedEvent(userId = userId, reason = "USER_INITIATED"))

        auditLogService.logEvent(
            userId, AuditAction.ACCOUNT_DEACTIVATED, "User", userId.toString(),
            "status=DEACTIVATED"
        )

        log.info("Account deactivated userId={}", userId)
    }

    /**
     * Request account deletion — GDPR Right to Erasure.
     * Creates a deletion request with 30-day grace period.
     */
    @Transactional
    fun requestDeletion(userId: Long, reason: String?): AccountDeletionRequestEntity {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        // Check if there's already a pending deletion request
        val existing = deletionRequestRepository.findByUserIdAndStatus(
            userId, AccountDeletionRequestEntity.STATUS_PENDING
        )
        if (existing != null) {
            throw IllegalStateException("A deletion request is already pending for this account")
        }

        val scheduledDeleteAt = Instant.now().plus(DELETION_GRACE_PERIOD_DAYS, ChronoUnit.DAYS)

        val request = AccountDeletionRequestEntity().apply {
            this.userId = userId
            this.reason = reason
            this.status = AccountDeletionRequestEntity.STATUS_PENDING
            this.requestedAt = Instant.now()
            this.scheduledDeleteAt = scheduledDeleteAt
        }

        val saved = deletionRequestRepository.save(request)

        // Mark user as PENDING_DELETION
        user.status = "PENDING_DELETION"
        userRepository.save(user)

        auditLogService.logEvent(
            userId, AuditAction.DELETION_REQUESTED, "User", userId.toString(),
            "scheduledDeleteAt=$scheduledDeleteAt reason=$reason"
        )

        log.info("Deletion request created userId={} scheduledAt={}", userId, scheduledDeleteAt)
        return saved
    }

    /**
     * Cancel a pending deletion request — only during grace period.
     */
    @Transactional
    fun cancelDeletion(userId: Long) {
        val request = deletionRequestRepository.findByUserIdAndStatus(
            userId, AccountDeletionRequestEntity.STATUS_PENDING
        ) ?: throw IllegalStateException("No pending deletion request found")

        request.status = AccountDeletionRequestEntity.STATUS_CANCELLED
        request.processedAt = Instant.now()
        request.processedBy = "USER"
        deletionRequestRepository.save(request)

        // Restore user status
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }
        user.status = "ACTIVE"
        userRepository.save(user)

        auditLogService.logEvent(
            userId, AuditAction.DELETION_CANCELLED, "User", userId.toString(), null
        )

        log.info("Deletion request cancelled userId={}", userId)
    }

    /**
     * Export user data — GDPR Right to Data Portability.
     * Generates a JSON export containing all user-related data.
     */
    @Transactional
    fun requestDataExport(userId: Long): AccountDataExportEntity {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        // Check if there's already a pending export
        val existing = dataExportRepository.findByUserIdAndStatus(
            userId, AccountDataExportEntity.STATUS_PENDING
        )
        if (existing != null) {
            throw IllegalStateException("A data export is already in progress")
        }

        val export = AccountDataExportEntity().apply {
            this.userId = userId
            this.status = AccountDataExportEntity.STATUS_PROCESSING
            this.requestedAt = Instant.now()
        }

        val saved = dataExportRepository.save(export)

        // Build export data
        try {
            val exportData = buildExportData(userId)
            val jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData)

            saved.status = AccountDataExportEntity.STATUS_COMPLETED
            saved.completedAt = Instant.now()
            saved.expiresAt = Instant.now().plus(EXPORT_EXPIRY_DAYS, ChronoUnit.DAYS)
            saved.fileSizeBytes = jsonContent.toByteArray().size.toLong()
            // In production, write to MinIO/S3. For now, store path reference.
            saved.filePath = "exports/user_${userId}_${saved.id}.json"
            dataExportRepository.save(saved)

            log.info("Data export completed userId={} exportId={} size={}",
                userId, saved.id, saved.fileSizeBytes)
        } catch (e: Exception) {
            saved.status = AccountDataExportEntity.STATUS_FAILED
            dataExportRepository.save(saved)
            log.error("Data export failed userId={}", userId, e)
        }

        return saved
    }

    /**
     * Get data export status.
     */
    fun getDataExport(exportId: Long): AccountDataExportEntity {
        return dataExportRepository.findById(exportId).orElseThrow {
            ResourceNotFoundException("DataExport", exportId)
        }
    }

    /**
     * Scheduled job — process deletion requests past grace period.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    fun processDeletionRequests() {
        val now = Instant.now()
        val requests = deletionRequestRepository.findByStatusAndScheduledDeleteAtBefore(
            AccountDeletionRequestEntity.STATUS_PENDING, now
        )

        if (requests.isEmpty()) {
            log.debug("GDPR_CLEANUP No deletion requests to process")
            return
        }

        requests.forEach { request ->
            try {
                performDeletion(request)
            } catch (e: Exception) {
                log.error("Failed to process deletion for userId={}", request.userId, e)
            }
        }

        log.info("GDPR_CLEANUP Processed {} deletion requests", requests.size)
    }

    /**
     * Perform permanent account deletion — anonymize PII data.
     */
    private fun performDeletion(request: AccountDeletionRequestEntity) {
        val userId = request.userId
        val user = userRepository.findById(userId).orElse(null) ?: return

        // Anonymize user data (GDPR compliant — not hard delete)
        user.username = "deleted_${user.id}"
        user.email = "deleted_${user.id}@redacted.local"
        user.passwordHash = "REDACTED"
        user.fullName = "Deleted User"
        user.phone = null
        user.avatarUrl = null
        user.status = "DELETED"
        userRepository.save(user)

        // Revoke all sessions
        loginSessionService.revokeAllSessions(userId, "GDPR_DELETION")

        // Publish cross-service deletion event (FR-009 GDPR) — account-service purges PII
        eventPublisher.publish(AccountDeletedEvent(userId = userId))

        // Update request status
        request.status = AccountDeletionRequestEntity.STATUS_COMPLETED
        request.processedAt = Instant.now()
        request.processedBy = "SYSTEM_SCHEDULER"
        deletionRequestRepository.save(request)

        log.info("GDPR_DELETION completed userId={}", userId)
    }

    /**
     * Build comprehensive data export for a user (GDPR Article 20).
     */
    private fun buildExportData(userId: Long): Map<String, Any?> {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        val domains = userDomainRepository.findAllByUserIdAndActiveTrue(userId)
        val sessions = loginSessionRepository.findByUserIdAndSessionActiveTrue(userId)

        return mapOf(
            "exportMetadata" to mapOf(
                "exportedAt" to Instant.now().toString(),
                "format" to "JSON",
                "version" to "1.0"
            ),
            "profile" to mapOf(
                "id" to user.id,
                "username" to user.username,
                "email" to user.email,
                "fullName" to user.fullName,
                "phone" to user.phone,
                "status" to user.status,
                "createdAt" to user.createdAt?.toString()
            ),
            "domainMemberships" to domains.map { d ->
                mapOf(
                    "domainId" to d.domainId,
                    "isPrimary" to d.isPrimary,
                    "joinedAt" to d.joinedAt?.toString()
                )
            },
            "activeSessions" to sessions.map { s ->
                mapOf(
                    "ipAddress" to s.ipAddress,
                    "deviceType" to s.deviceType,
                    "browserName" to s.browserName,
                    "loginAt" to s.loginAt?.toString()
                )
            }
        )
    }
}
