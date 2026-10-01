package com.ntt.authservice.shared.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.basecore.i18n.I18nCacheInvalidationListener
import com.ntt.basecore.i18n.RedisMessageSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale
import java.util.Optional

/**
 * Auth-service i18n configuration.
 *
 * Provides:
 * - LocaleResolver: AcceptHeaderLocaleResolver with supported locales [en, vi], default en.
 * - MessageSource: RedisMessageSource (Caffeine L1 + Redis L2) with file bundle fallback.
 *   If Redis is not available (e.g. test profile), falls back gracefully to ReloadableResourceBundleMessageSource.
 * - Pub/Sub listener for cache invalidation when Redis is available.
 */
@Configuration
class I18nConfig {

    @Bean
    fun localeResolver(): LocaleResolver {
        val resolver = AcceptHeaderLocaleResolver()
        resolver.supportedLocales = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi"))
        resolver.setDefaultLocale(Locale.ENGLISH)
        return resolver
    }

    @Bean
    fun messageSource(
        redisTemplate: Optional<StringRedisTemplate>,
        @Value("\${base.i18n.basenames:classpath:messages/auth-messages,classpath:messages/messages}")
        basenamesProp: String
    ): MessageSource {
        val fileBundleSource = ReloadableResourceBundleMessageSource()
        val basenames = basenamesProp.split(",").map { it.trim() }.toTypedArray()
        fileBundleSource.setBasenames(*basenames)
        fileBundleSource.setDefaultEncoding("UTF-8")
        fileBundleSource.setFallbackToSystemLocale(false)
        fileBundleSource.setUseCodeAsDefaultMessage(false)

        return if (redisTemplate.isPresent) {
            val redisSource = RedisMessageSource(redisTemplate.get())
            redisSource.setParentMessageSource(fileBundleSource)
            redisSource
        } else {
            fileBundleSource
        }
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory::class)
    fun i18nCacheInvalidationListener(
        messageSource: MessageSource,
        objectMapper: ObjectMapper
    ): I18nCacheInvalidationListener? {
        val redisSource = messageSource as? RedisMessageSource ?: return null
        return I18nCacheInvalidationListener(redisSource, objectMapper)
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory::class)
    fun i18nCacheListenerContainer(
        connectionFactory: RedisConnectionFactory,
        i18nCacheInvalidationListener: Optional<I18nCacheInvalidationListener>
    ): RedisMessageListenerContainer? {
        if (i18nCacheInvalidationListener.isEmpty) return null
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)

        val adapter = MessageListenerAdapter(i18nCacheInvalidationListener.get(), "onMessage")
        container.addMessageListener(adapter, ChannelTopic(I18nCacheInvalidationListener.CHANNEL))
        return container
    }
}

