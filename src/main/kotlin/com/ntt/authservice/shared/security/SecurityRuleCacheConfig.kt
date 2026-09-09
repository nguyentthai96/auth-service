package com.ntt.authservice.shared.security

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Redis pub/sub listener for security rule cache invalidation.
 *
 * Channel: auth-service:security-rules:changed
 *
 * When a rule is created/updated/deleted via Admin API,
 * a message is published → all instances evict their L1 cache.
 *
 * Only activates when RedisConnectionFactory bean exists.
 */
@Configuration
@ConditionalOnBean(RedisConnectionFactory::class)
class SecurityRuleCacheConfig {

    companion object {
        const val CHANNEL = "auth-service:security-rules:changed"
    }

    @Bean
    fun securityRuleListenerContainer(
        connectionFactory: RedisConnectionFactory,
        dynamicAuthorizationManager: DynamicAuthorizationManager
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)

        val listener = MessageListenerAdapter(
            SecurityRuleCacheListener(dynamicAuthorizationManager),
            "onMessage"
        )
        container.addMessageListener(listener, ChannelTopic(CHANNEL))

        return container
    }
}

/**
 * Listener that evicts DynamicAuthorizationManager cache on Redis message.
 */
class SecurityRuleCacheListener(
    private val dynamicAuthorizationManager: DynamicAuthorizationManager
) : MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val body = String(message.body)
        log.info("Security rule change notification received: {}", body)
        dynamicAuthorizationManager.evictCache()
    }
}
