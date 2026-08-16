package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.AccountDataExportEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountDataExportRepository : JpaRepository<AccountDataExportEntity, Long> {

    fun findByUserIdAndStatus(userId: Long, status: String): AccountDataExportEntity?

    fun findByUserId(userId: Long): List<AccountDataExportEntity>
}
