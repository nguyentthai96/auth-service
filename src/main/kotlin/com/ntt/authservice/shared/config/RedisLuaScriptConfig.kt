package com.ntt.authservice.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.DefaultRedisScript

/**
 * Spring configuration for Redis Lua script beans.
 * Each script is loaded once from classpath — DefaultRedisScript caches SHA1
 * for automatic EVALSHA optimization after first execution.
 *
 * Scripts:
 * - sliding_window_rate_limit.lua — weighted sliding window rate limiter (FR-002)
 * - safe_lock_release.lua — ownership-checked distributed lock release (FR-004)
 * - atomic_data_store.lua — TOCTOU-safe data store with size enforcement (FR-007)
 */
@Configuration
class RedisLuaScriptConfig {

    @Bean
    fun slidingWindowRateLimitScript(): DefaultRedisScript<Long> {
        val script = DefaultRedisScript<Long>()
        script.setLocation(ClassPathResource("redis/sliding_window_rate_limit.lua"))
        script.resultType = Long::class.java
        return script
    }

    @Bean
    fun safeLockReleaseScript(): DefaultRedisScript<Long> {
        val script = DefaultRedisScript<Long>()
        script.setLocation(ClassPathResource("redis/safe_lock_release.lua"))
        script.resultType = Long::class.java
        return script
    }

    @Bean
    fun atomicDataStoreScript(): DefaultRedisScript<Long> {
        val script = DefaultRedisScript<Long>()
        script.setLocation(ClassPathResource("redis/atomic_data_store.lua"))
        script.resultType = Long::class.java
        return script
    }
}
