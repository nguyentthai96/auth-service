package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.AccountDeletionRequestEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AccountDeletionRequestRepository : JpaRepository<AccountDeletionRequestEntity, Long> {

    fun findByUserIdAndStatus(userId: Long, status: String): AccountDeletionRequestEntity?

    fun findByStatusAndScheduledDeleteAtBefore(status: String, cutoff: Instant): List<AccountDeletionRequestEntity>
}
