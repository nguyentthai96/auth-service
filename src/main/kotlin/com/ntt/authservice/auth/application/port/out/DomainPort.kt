package com.ntt.authservice.auth.application.port.out

/**
 * Outbound port for domain persistence.
 */
interface DomainPort {
    fun findByCodeAndActive(code: String): DomainInfo?
    fun findById(domainId: Long): DomainInfo?
}

/**
 * Lightweight domain info returned by port — avoids coupling to JPA entity.
 */
data class DomainInfo(
    val id: Long,
    val code: String,
    val name: String? = null
)
