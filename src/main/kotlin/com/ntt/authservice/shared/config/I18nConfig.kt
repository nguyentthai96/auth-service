package com.ntt.authservice.shared.config

import com.ntt.authservice.shared.i18n.DatabaseMessageSource
import com.ntt.authservice.shared.i18n.I18nMessageRepository
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

/**
 * Auth-service i18n configuration — overrides base-core's defaults.
 *
 * Provides:
 * - LocaleResolver: AcceptHeaderLocaleResolver with supported locales [en, vi], default en.
 *   Overrides base-core's I18nAutoConfiguration localeResolver (same config — explicit for clarity).
 * - CompositeMessageSource chain: DatabaseMessageSource → file bundles.
 *
 * Resolution priority:
 * 1. DatabaseMessageSource (i18n_messages table — Caffeine cached)
 * 2. File bundles: auth-messages_{locale} → auth-messages → messages_{locale} → messages
 *
 * FR-002: Locale resolution from Accept-Language header.
 * FR-003: Message bundle infrastructure.
 * FR-012: Supported locales whitelist.
 * FR-013: Idempotent locale resolution (AcceptHeaderLocaleResolver is stateless).
 */
@Configuration
class I18nConfig {

    /**
     * Locale resolver — reads Accept-Language header and resolves to supported locale.
     * Unsupported locales (e.g., ja, km) fall back to English.
     * Overrides base-core's @ConditionalOnMissingBean localeResolver.
     */
    @Bean
    fun localeResolver(): LocaleResolver {
        val resolver = AcceptHeaderLocaleResolver()
        resolver.supportedLocales = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi"))
        resolver.setDefaultLocale(Locale.ENGLISH)
        return resolver
    }

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
