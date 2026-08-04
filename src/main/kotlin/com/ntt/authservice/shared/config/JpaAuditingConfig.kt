package com.ntt.authservice.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

/**
 * Custom AuditorAware for auth-service.
 * Note: @EnableJpaAuditing is already provided by base-core's JpaAuditorConfiguration.
 * This class only defines the custom auditor resolver using SecurityContext.
 */
@Configuration
class JpaAuditingConfig {

    @Bean
    fun securityAuditorAware(): AuditorAware<String> = SecurityAuditorAware()
}

/**
 * Resolves the current auditor (username) from SecurityContext.
 * Returns "system" for unauthenticated operations (e.g., registration, scheduled tasks).
 */
class SecurityAuditorAware : AuditorAware<String> {

    override fun getCurrentAuditor(): Optional<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        return if (authentication != null && authentication.isAuthenticated) {
            Optional.of(authentication.principal as? String ?: "system")
        } else {
            Optional.of("system")
        }
    }
}
