package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.application.port.out.DomainPort
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserDomainEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserDomainRepository
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Tests for DomainLookupService.getPrimaryDomainId() (Design Gap D).
 * Verifies: primary domain resolution, fallback to first membership, error on no membership.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("DomainLookupService — getPrimaryDomainId Tests")
class DomainLookupServiceTest {

    @Mock private lateinit var userDomainRepository: UserDomainRepository
    @Mock private lateinit var domainPort: DomainPort

    private lateinit var domainLookupService: DomainLookupService

    @BeforeEach
    fun setUp() {
        domainLookupService = DomainLookupService(userDomainRepository, domainPort)
    }

    @Test
    @DisplayName("should return primary domain ID when isPrimary=true membership exists")
    fun shouldReturnPrimaryDomainId() {
        val memberships = listOf(
            UserDomainEntity().apply { domainId = 200L; isPrimary = false },
            UserDomainEntity().apply { domainId = 100L; isPrimary = true }
        )
        whenever(userDomainRepository.findAllByUserIdAndActiveTrue(1L)).thenReturn(memberships)

        val result = domainLookupService.getPrimaryDomainId(1L)

        assertEquals(100L, result)
    }

    @Test
    @DisplayName("should return first membership domain ID when no primary exists")
    fun shouldReturnFirstMembershipDomainIdWhenNoPrimary() {
        val memberships = listOf(
            UserDomainEntity().apply { domainId = 300L; isPrimary = false },
            UserDomainEntity().apply { domainId = 400L; isPrimary = false }
        )
        whenever(userDomainRepository.findAllByUserIdAndActiveTrue(1L)).thenReturn(memberships)

        val result = domainLookupService.getPrimaryDomainId(1L)

        assertEquals(300L, result)
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when no memberships exist")
    fun shouldThrowWhenNoMemberships() {
        whenever(userDomainRepository.findAllByUserIdAndActiveTrue(999L)).thenReturn(emptyList())

        assertThrows<ResourceNotFoundException> {
            domainLookupService.getPrimaryDomainId(999L)
        }
    }
}
