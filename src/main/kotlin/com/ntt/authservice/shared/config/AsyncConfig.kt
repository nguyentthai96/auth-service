package com.ntt.authservice.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor

/**
 * Async configuration for non-blocking operations.
 * Provides dedicated thread pools for audit logging and other background tasks.
 */
@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * Dedicated thread pool for audit log persistence.
     * Uses CallerRunsPolicy as fallback — if the pool is saturated,
     * the audit write runs synchronously on the caller thread (graceful degradation).
     */
    @Bean("auditExecutor")
    fun auditExecutor(): TaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 5
        queueCapacity = 100
        setThreadNamePrefix("audit-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        initialize()
    }
}
