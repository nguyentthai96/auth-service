package com.ntt.authservice.config

import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * Test configuration providing mock Redis template beans for @SpringBootTest integration tests
 * that run without a real Redis instance.
 *
 * Strategy:
 * - DataRedisAutoConfiguration is EXCLUDED in application-test.yml
 * - We intentionally do NOT provide a RedisConnectionFactory bean,
 *   which prevents ALL RedisMessageListenerContainer beans from being created
 *   (both SecurityRuleCacheConfig and DataRedisAnnotationDrivenConfiguration
 *    have @ConditionalOnBean(RedisConnectionFactory::class))
 * - We provide mock StringRedisTemplate and RedisTemplate directly
 *   so services that depend on them can still be wired
 * - RedisConfig (which creates redisTemplate from RedisConnectionFactory)
 *   is excluded via @Profile("!test") — see below
 */
@Configuration
@Profile("test")
class TestRedisConfig {

    @Bean
    @Primary
    fun stringRedisTemplate(): StringRedisTemplate {
        val template = Mockito.mock(StringRedisTemplate::class.java)

        @Suppress("UNCHECKED_CAST")
        val valueOps = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        whenever(template.opsForValue()).thenReturn(valueOps)

        return template
    }

    @Bean
    @Primary
    fun redisTemplate(): RedisTemplate<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
    }
}
