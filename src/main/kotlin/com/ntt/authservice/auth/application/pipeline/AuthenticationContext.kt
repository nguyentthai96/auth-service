package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.application.PromotionResult
import com.ntt.authservice.auth.application.command.LoginCommand
import com.ntt.authservice.auth.application.port.out.DomainInfo
import com.ntt.authservice.auth.domain.model.User

/**
 * Immutable authentication context propagated through the pipeline.
 * OQ-002 decision: data class for thread-safety and safe pipeline-style state propagation.
 *
 * Each step produces a new context via Kotlin copy() — no mutation.
 *
 * Performance: ~5 copy operations per login flow (negligible overhead).
 */
data class AuthenticationContext(
    /** The original login command (immutable input). */
    val command: LoginCommand,

    /** Resolved user entity (populated by SecurityPreCheckStep). */
    val user: User? = null,

    /** Active domain code (populated by PolicyEnforcementStep). */
    val domainCode: String? = null,

    /** Domain info (populated by PolicyEnforcementStep). */
    val domain: DomainInfo? = null,

    /** User roles in active domain (populated by PolicyEnforcementStep). */
    val roles: List<Any> = emptyList(),

    /** Login session entity (populated by SessionEstablishmentStep). */
    val loginSession: LoginSessionEntity? = null,

    /** Anonymous session promotion result (populated by SessionEstablishmentStep). */
    val promotionResult: PromotionResult? = null,

    /** Generated auth response (populated by TokenIssuanceStep). */
    val authResponse: AuthResponse? = null
)
