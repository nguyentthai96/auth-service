package com.ntt.authservice.auth.domain.service

import com.ntt.authservice.auth.application.port.out.DomainPort
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserDomainRepository
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.springframework.stereotype.Component

/**
 * Domain lookup service — extracted from AuthService.getPrimaryDomain().
 * Resolves the primary domain for a user based on membership.
 */
@Component
class DomainLookupService(
    private val userDomainRepository: UserDomainRepository,
    private val domainPort: DomainPort
) {

    /**
     * Find the primary domain code for a user.
     * Priority: isPrimary flag → first active membership → error.
     */
    fun getPrimaryDomainCode(userId: Long): String {
        val membership = userDomainRepository.findAllByUserIdAndActiveTrue(userId)
            .firstOrNull { it.isPrimary }
            ?: userDomainRepository.findAllByUserIdAndActiveTrue(userId).firstOrNull()
            ?: throw ResourceNotFoundException("DomainMembership", userId)

        val domain = domainPort.findById(membership.domainId)
            ?: throw ResourceNotFoundException("Domain", membership.domainId)

        return domain.code
    }
}
