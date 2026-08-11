package com.ntt.authservice.shared.config

import com.ntt.authservice.shared.i18n.DatabaseMessageSource
import com.ntt.authservice.shared.i18n.I18nMessageRepository
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.ReloadableResourceBundleMessageSource

/**
 * Auth-service i18n configuration — overrides base-core's default MessageSource.
 * Provides CompositeMessageSource chain: DatabaseMessageSource → file bundles.
 *
 * Resolution priority:
 * 1. DatabaseMessageSource (i18n_messages table — Caffeine cached)
 * 2. File bundles: auth-messages_{locale} → auth-messages → messages_{locale} → messages
 */
@Configuration
class I18nConfig {

    @Bean
    @Primary
    fun messageSource(i18nMessageRepository: I18nMessageRepository): MessageSource {
        // File-based message source (fallback)
        val fileMessageSource = ReloadableResourceBundleMessageSource()
        fileMessageSource.setBasenames(
            "classpath:messages/auth-messages",
            "classpath:messages/messages"
        )
        fileMessageSource.setDefaultEncoding("UTF-8")
        fileMessageSource.setFallbackToSystemLocale(false)
        fileMessageSource.setUseCodeAsDefaultMessage(false)

        // Database message source (priority) — chains to file source on miss
        val dbMessageSource = DatabaseMessageSource(i18nMessageRepository)
        dbMessageSource.setParentMessageSource(fileMessageSource)

        return dbMessageSource
    }
}
