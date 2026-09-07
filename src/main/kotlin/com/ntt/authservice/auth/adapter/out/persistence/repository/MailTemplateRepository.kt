package com.ntt.authservice.auth.adapter.out.persistence.repository

import com.ntt.authservice.auth.adapter.out.persistence.entity.MailTemplateEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Repository for mail templates — lookup by template code.
 */
interface MailTemplateRepository : JpaRepository<MailTemplateEntity, Long> {

    fun findByCodeAndActiveTrue(code: String): MailTemplateEntity?
}
