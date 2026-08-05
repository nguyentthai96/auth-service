package com.ntt.authservice.auth.adapter.out.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import com.ntt.authservice.auth.application.port.out.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Spring ApplicationEvent publisher adapter.
 * Phase 1: Logs events only.
 * Phase 3: Will be replaced by Kafka publisher.
 */
@Component
class SpringEventPublisher : EventPublisher {

    private val log = LoggerFactory.getLogger(SpringEventPublisher::class.java)

    override fun publish(event: DomainEvent) {
        log.info("Domain event published: type={}, payload={}", event.eventType, event)
        // Phase 3: Replace with Kafka/Spring Cloud Stream publish
    }
}
