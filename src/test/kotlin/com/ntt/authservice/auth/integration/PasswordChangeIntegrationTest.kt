package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.application.PasswordPolicyService
import com.ntt.authservice.rbac.adapter.out.persistence.entity.PasswordHistoryEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.PasswordPolicyEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.*

/**
 * Integration-style tests for Password Change flow (FR-013, FR-014).
 * Tests: policy enforcement, history check, wrong old password, default policy fallback.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("Password Change Integration Tests")
class PasswordChangeIntegrationTest {

    @Mock private lateinit var passwordPolicyRepository: PasswordPolicyRepository
    @Mock private lateinit var passwordHistoryRepository: PasswordHistoryRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var auditLogService: AuditLogService

    private val passwordEncoder = BCryptPasswordEncoder(4) // Low strength for faster tests

    private lateinit var passwordPolicyService: PasswordPolicyService
    private lateinit var securityProperties: SecurityProperties

    private val testUserId = 1L
    private val testDomainId = 100L

    @BeforeEach
    fun setUp() {
        securityProperties = SecurityProperties(
            password = SecurityProperties.PasswordProperties(
                bcryptStrength = 4,
                maxFailedAttempts = 3,
                lockDurationMinutes = 15
            )
        )
        passwordPolicyService = PasswordPolicyService(
            passwordPolicyRepository, passwordHistoryRepository,
            userRepository, securityProperties, passwordEncoder, auditLogService
        )
    }

    // ── TC1: Change password with valid policy → success + history entry ──

    @Test
    @DisplayName("TC1: Change password with valid policy should succeed and create history entry")
    fun shouldChangePasswordSuccessfully() {
        val currentHash = passwordEncoder.encode("OldPass123!")
        val user = UserEntity().apply {
            id = testUserId
            passwordHash = currentHash
        }
        val policy = createPolicy(testDomainId, minLength = 8, historyCount = 3)

        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(passwordPolicyRepository.findByDomainId(testDomainId)).thenReturn(policy)
        whenever(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(testUserId)).thenReturn(emptyList())
        whenever(passwordHistoryRepository.save(any<PasswordHistoryEntity>())).thenAnswer { it.arguments[0] }
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        assertDoesNotThrow {
            passwordPolicyService.changePassword(testUserId, "OldPass123!", "NewStrongPass456!", testDomainId)
        }

        verify(passwordHistoryRepository).save(argThat<PasswordHistoryEntity> {
            userId == testUserId && passwordHash.isNotBlank()
        })
        verify(userRepository).save(argThat<UserEntity> {
            passwordHash != currentHash && passwordChangedAt != null
        })
        verify(auditLogService).logEvent(eq(testUserId), any(), any(), any(), any())
    }

    // ── TC2: Change to recently used password → 400 PASSWORD_RECENTLY_USED ──

    @Test
    @DisplayName("TC2: Change to recently used password should throw PasswordRecentlyUsedException")
    fun shouldRejectRecentlyUsedPassword() {
        val currentHash = passwordEncoder.encode("CurrentPass123!")
        val oldHash = passwordEncoder.encode("OldReusedPass789!")
        val user = UserEntity().apply {
            id = testUserId
            passwordHash = currentHash
        }
        val policy = createPolicy(testDomainId, minLength = 8, historyCount = 3)
        val historyEntry = PasswordHistoryEntity().apply {
            userId = testUserId
            passwordHash = oldHash
            createdAt = Instant.now().minusSeconds(3600)
        }

        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(passwordPolicyRepository.findByDomainId(testDomainId)).thenReturn(policy)
        whenever(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(testUserId))
            .thenReturn(listOf(historyEntry))

        assertThrows<PasswordRecentlyUsedException> {
            passwordPolicyService.changePassword(testUserId, "CurrentPass123!", "OldReusedPass789!", testDomainId)
        }
    }

    // ── TC3: Change with weak password → 400 PASSWORD_POLICY_VIOLATION ──

    @Test
    @DisplayName("TC3: Change with weak password should throw PasswordPolicyViolationException")
    fun shouldRejectWeakPassword() {
        val currentHash = passwordEncoder.encode("CurrentPass123!")
        val user = UserEntity().apply {
            id = testUserId
            passwordHash = currentHash
        }
        val policy = createPolicy(testDomainId, minLength = 12, requireUppercase = true, requireDigit = true, requireSpecial = true)

        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(passwordPolicyRepository.findByDomainId(testDomainId)).thenReturn(policy)

        // "short" doesn't meet minLength=12, uppercase, digit, special requirements
        assertThrows<PasswordPolicyViolationException> {
            passwordPolicyService.changePassword(testUserId, "CurrentPass123!", "short", testDomainId)
        }
    }

    // ── TC4: Change with wrong old password → 401 INVALID_CREDENTIALS ──

    @Test
    @DisplayName("TC4: Change with wrong old password should throw InvalidCredentialsException")
    fun shouldRejectWrongOldPassword() {
        val currentHash = passwordEncoder.encode("CorrectOldPass!")
        val user = UserEntity().apply {
            id = testUserId
            passwordHash = currentHash
        }

        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))

        assertThrows<InvalidCredentialsException> {
            passwordPolicyService.changePassword(testUserId, "WrongOldPass!", "NewPass456!", testDomainId)
        }
    }

    // ── TC5: Default policy applied when no domain-specific policy exists ──

    @Test
    @DisplayName("TC5: Should apply default policy when no domain-specific policy exists")
    fun shouldApplyDefaultPolicyWhenNoDomainPolicy() {
        val currentHash = passwordEncoder.encode("OldPass123!")
        val user = UserEntity().apply {
            id = testUserId
            passwordHash = currentHash
        }

        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(passwordPolicyRepository.findByDomainId(testDomainId)).thenReturn(null)
        whenever(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(testUserId)).thenReturn(emptyList())
        whenever(passwordHistoryRepository.save(any<PasswordHistoryEntity>())).thenAnswer { it.arguments[0] }
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        // Should succeed with default policy (lenient defaults)
        assertDoesNotThrow {
            passwordPolicyService.changePassword(testUserId, "OldPass123!", "NewStrongPassword456!", testDomainId)
        }

        verify(userRepository).save(any())
    }

    private fun createPolicy(
        domainId: Long,
        minLength: Int = 8,
        maxLength: Int = 128,
        requireUppercase: Boolean = false,
        requireLowercase: Boolean = false,
        requireDigit: Boolean = false,
        requireSpecial: Boolean = false,
        historyCount: Int = 3,
        maxAgeDays: Int = 90
    ): PasswordPolicyEntity {
        return PasswordPolicyEntity().apply {
            this.domainId = domainId
            this.minLength = minLength
            this.maxLength = maxLength
            this.requireUppercase = requireUppercase
            this.requireLowercase = requireLowercase
            this.requireDigit = requireDigit
            this.requireSpecial = requireSpecial
            this.historyCount = historyCount
            this.maxAgeDays = maxAgeDays
        }
    }
}
