package com.ntt.authservice.auth.application

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

    /**
     * Find the primary domain ID for a user.
     * Used by password change flow to resolve domain-scoped password policy.
     * Priority: isPrimary flag → first active membership → error.
     */
    fun getPrimaryDomainId(userId: Long): Long {
        val membership = userDomainRepository.findAllByUserIdAndActiveTrue(userId)
            .firstOrNull { it.isPrimary }
            ?: userDomainRepository.findAllByUserIdAndActiveTrue(userId).firstOrNull()
            ?: throw ResourceNotFoundException("DomainMembership", userId)

        return membership.domainId
    }

    /**
     * Get domain branding configuration (FR-016).
     * Returns branding fields: logo, colors, login page config.
     */
    fun getDomainBranding(domainCode: String): DomainBrandingInfo {
        val domain = domainPort.findByCodeAndActive(domainCode)
            ?: throw ResourceNotFoundException("Domain", domainCode)

        return DomainBrandingInfo(
            domainCode = domain.code,
            domainName = domain.name ?: domain.code,
            logoUrl = domain.logoUrl,
            primaryColor = domain.primaryColor,
            loginPageConfig = domain.loginPageConfig,
            faviconUrl = domain.faviconUrl
        )
    }

    data class DomainBrandingInfo(
        val domainCode: String,
        val domainName: String,
        val logoUrl: String?,
        val primaryColor: String?,
        val loginPageConfig: String?,
        val faviconUrl: String?
    )
}
