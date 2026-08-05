package com.ntt.authservice.auth.adapter.out.persistence

import com.ntt.authservice.auth.application.port.out.DomainInfo
import com.ntt.authservice.auth.application.port.out.DomainPort
import com.ntt.authservice.rbac.adapter.out.persistence.repository.DomainRepository
import org.springframework.stereotype.Component

/**
 * JPA adapter implementing DomainPort.
 */
@Component
class DomainPersistenceAdapter(
    private val domainRepository: DomainRepository
) : DomainPort {

    override fun findByCodeAndActive(code: String): DomainInfo? {
        return domainRepository.findByCodeAndActiveTrue(code)?.let {
            DomainInfo(id = it.id!!, code = it.code, name = it.name)
        }
    }

    override fun findById(domainId: Long): DomainInfo? {
        return domainRepository.findById(domainId).orElse(null)?.let {
            DomainInfo(id = it.id!!, code = it.code, name = it.name)
        }
    }
}
