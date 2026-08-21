package com.ntt.authservice.shared.config

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Observability configuration for enabling @Observed annotation support.
 *
 * Registers ObservedAspect bean which enables AOP-based interception of
 * methods annotated with @Observed, creating Micrometer Observation spans.
 *
 * Uses @ConditionalOnMissingBean to avoid conflict if base-observability-starter
 * already auto-configures this bean.
 *
 * @see io.micrometer.observation.annotation.Observed
 */
@Configuration
class ObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean
    fun observedAspect(registry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(registry)
    }
}
