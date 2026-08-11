package com.ntt.authservice.shared.i18n

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.context.support.AbstractMessageSource
import java.text.MessageFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Database-backed MessageSource using i18n_messages table.
 * Extends AbstractMessageSource for Spring MessageSource chain integration.
 *
 * Resolution chain: DB cache → DB query → parent MessageSource (file bundles).
 * Cache: Caffeine with 5-minute TTL to avoid DB hit per request.
 * DB unavailable: logs warning, returns null → parent chain handles fallback.
 */
class DatabaseMessageSource(
    private val repository: I18nMessageRepository
) : AbstractMessageSource() {

    private val cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, String>()

    override fun resolveCode(code: String, locale: Locale): MessageFormat? {
        val cacheKey = "${code}:${locale.language}"

        // Check cache first
        val cached = cache.getIfPresent(cacheKey)
        if (cached != null) {
            return createMessageFormat(cached, locale)
        }

        // Query DB
        return try {
            val entity = repository.findByCodeAndLocaleAndIsActiveTrue(code, locale.language)
            if (entity != null) {
                cache.put(cacheKey, entity.message)
                createMessageFormat(entity.message, locale)
            } else {
                // Return null — AbstractMessageSource will delegate to parent
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to resolve i18n message from DB: code={}, locale={}", code, locale, e)
            // DB unavailable — return null, let parent chain handle
            null
        }
    }

    private fun createMessageFormat(pattern: String, locale: Locale): MessageFormat {
        return MessageFormat(pattern, locale)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DatabaseMessageSource::class.java)
    }
}
