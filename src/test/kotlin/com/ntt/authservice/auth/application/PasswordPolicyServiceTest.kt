package com.ntt.authservice.auth.application

import com.ntt.authservice.rbac.adapter.out.persistence.entity.PasswordHistoryEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.PasswordPolicyEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.PasswordHistoryRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.PasswordPolicyRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.InvalidCredentialsException
import com.ntt.authservice.shared.exception.PasswordPolicyViolationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.*

/**
 * Unit tests for PasswordPolicyService — validates password strength, history, expiry, and change flow.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("PasswordPolicyService Tests")
class PasswordPolicyServiceTest {

    @Mock private lateinit var passwordPolicyRepository: PasswordPolicyRepository
    @Mock private lateinit var passwordHistoryRepository: PasswordHistoryRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var service: PasswordPolicyService

    private val defaultPolicy = PasswordPolicyEntity().apply {
        domainId = 1L
        minLength = 8
        maxLength = 128
        requireUppercase = true
        requireLowercase = true
        requireDigit = true
        requireSpecial = true
        historyCount = 3
        maxAgeDays = 90
    }

    @BeforeEach
    fun setUp() {
        service = PasswordPolicyService(
            passwordPolicyRepository, passwordHistoryRepository,
            userRepository, securityProperties, passwordEncoder
        )
    }

    @Test
    @DisplayName("should validate strong password against policy")
    fun shouldValidateStrongPassword() {
        whenever(passwordPolicyRepository.findByDomainId(1L)).thenReturn(defaultPolicy)

        val violations = service.validatePasswordStrength("SecureP@ss123", 1L)

        assertTrue(violations.isEmpty())
    }

    @Test
    @DisplayName("should return violations for weak password")
    fun shouldRejectWeakPassword() {
        whenever(passwordPolicyRepository.findByDomainId(1L)).thenReturn(defaultPolicy)

        val violations = service.validatePasswordStrength("short", 1L)

        assertTrue(violations.isNotEmpty())
    }

    @Test
    @DisplayName("should check password history — reject recently used")
    fun shouldRejectRecentlyUsedPassword() {
        val history = listOf(
            PasswordHistoryEntity().apply {
                userId = 1L
                passwordHash = "\$2a\$10\$oldHash1"
                createdAt = Instant.now()
            }
        )
        whenever(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(history)
        whenever(passwordEncoder.matches("OldPassword1!", "\$2a\$10\$oldHash1")).thenReturn(true)

        val allowed = service.checkPasswordHistory(1L, "OldPassword1!", 3)

        assertFalse(allowed)
    }

    @Test
    @DisplayName("should allow password not in history")
    fun shouldAllowNewPassword() {
        val history = listOf(
            PasswordHistoryEntity().apply {
                userId = 1L
                passwordHash = "\$2a\$10\$oldHash1"
                createdAt = Instant.now()
            }
        )
        whenever(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(history)
        whenever(passwordEncoder.matches("BrandNewP@ss1", "\$2a\$10\$oldHash1")).thenReturn(false)

        val allowed = service.checkPasswordHistory(1L, "BrandNewP@ss1", 3)

        assertTrue(allowed)
    }

    @Test
    @DisplayName("should throw InvalidCredentialsException for wrong old password")
    fun shouldRejectWrongOldPassword() {
        val user = UserEntity().apply {
            passwordHash = "\$2a\$10\$currentHash"
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches("wrongOld", "\$2a\$10\$currentHash")).thenReturn(false)

        assertThrows<InvalidCredentialsException> {
            service.changePassword(1L, "wrongOld", "NewSecure@1", 1L)
        }
    }

    @Test
    @DisplayName("should throw PasswordPolicyViolationException for weak new password")
    fun shouldRejectWeakNewPassword() {
        val user = UserEntity().apply {
            passwordHash = "\$2a\$10\$currentHash"
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches("correctOld", "\$2a\$10\$currentHash")).thenReturn(true)
        whenever(passwordPolicyRepository.findByDomainId(1L)).thenReturn(defaultPolicy)

        assertThrows<PasswordPolicyViolationException> {
            service.changePassword(1L, "correctOld", "weak", 1L)
        }
    }

    @Test
    @DisplayName("should detect expired password")
    fun shouldDetectExpiredPassword() {
        val user = UserEntity().apply {
            passwordChangedAt = Instant.now().minusSeconds(100 * 86400) // 100 days ago
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(passwordPolicyRepository.findByDomainId(1L)).thenReturn(defaultPolicy)

        val expired = service.isPasswordExpired(1L, 1L)

        assertTrue(expired)
    }

    @Test
    @DisplayName("should not expire password when maxAgeDays is 0")
    fun shouldNotExpireWhenMaxAgeZero() {
        val policyNoExpiry = PasswordPolicyEntity().apply {
            domainId = 1L
            maxAgeDays = 0
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(UserEntity()))
        whenever(passwordPolicyRepository.findByDomainId(1L)).thenReturn(policyNoExpiry)

        val expired = service.isPasswordExpired(1L, 1L)

        assertFalse(expired)
    }

    @Test
    @DisplayName("should expire password when passwordChangedAt is null")
    fun shouldExpireWhenNeverChanged() {
        val user = UserEntity().apply {
            passwordChangedAt = null
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(passwordPolicyRepository.findByDomainId(1L)).thenReturn(defaultPolicy)

        val expired = service.isPasswordExpired(1L, 1L)

        assertTrue(expired)
    }
}
