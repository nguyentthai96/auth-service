package com.ntt.authservice.shared.i18n

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for i18n_messages table.
 * Provides lookup methods for DatabaseMessageSource.
 */
@Repository
interface I18nMessageRepository : JpaRepository<I18nMessageEntity, Long> {

    /**
     * Find active message by code and locale.
     * Used by DatabaseMessageSource.resolveCode() for single key resolution.
     */
    fun findByCodeAndLocaleAndIsActiveTrue(code: String, locale: String): I18nMessageEntity?

    /**
     * Find all active messages for a module and locale.
     * Useful for bulk preloading or admin operations.
     */
    fun findByModuleAndLocaleAndIsActiveTrue(module: String, locale: String): List<I18nMessageEntity>
}
