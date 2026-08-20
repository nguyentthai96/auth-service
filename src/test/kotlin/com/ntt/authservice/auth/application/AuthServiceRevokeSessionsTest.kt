package com.ntt.authservice.auth.application

import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.*
import com.ntt.authservice.rbac.application.RbacEngine
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.*

/**
 * Tests for AuthService.revokeAllSessions() (FR-012 — Force Logout, Design Gap A).
 * Verifies: refresh token revocation, audit logging, return count, user validation.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("AuthService — revokeAllSessions Tests")
class AuthServiceRevokeSessionsTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var userDomainRepository: UserDomainRepository
    @Mock private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock private lateinit var tokenBlacklistRepository: TokenBlacklistRepository
    @Mock private lateinit var domainRepository: DomainRepository
    @Mock private lateinit var rbacEngine: RbacEngine
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var captchaVerifier: CaptchaVerifier
    @Mock private lateinit var mfaService: MfaService
    @Mock private lateinit var passwordPolicyService: PasswordPolicyService
    @Mock private lateinit var auditLogService: AuditLogService

    private lateinit var authService: AuthService

    private val testUserId = 42L

    @BeforeEach
    fun setUp() {
        val securityProperties = SecurityProperties()
        val passwordEncoder = BCryptPasswordEncoder(4)
        authService = AuthService(
            userRepository, userDomainRepository, refreshTokenRepository,
            tokenBlacklistRepository, domainRepository, rbacEngine,
            jwtService, passwordEncoder, securityProperties,
            captchaVerifier, mfaService, passwordPolicyService, auditLogService
        )
    }

    @Test
    @DisplayName("should revoke all refresh tokens and return count")
    fun shouldRevokeAllRefreshTokensAndReturnCount() {
        val user = UserEntity().apply { id = testUserId }
        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(refreshTokenRepository.revokeAllByUserId(testUserId)).thenReturn(5)

        val result = authService.revokeAllSessions(testUserId)

        assertEquals(5, result)
        verify(refreshTokenRepository).revokeAllByUserId(testUserId)
        verify(auditLogService).logEvent(
            eq(testUserId),
            eq(AuditAction.SESSION_REVOKED),
            eq("User"),
            eq(testUserId.toString()),
            argThat { contains("revokedTokens=5") }
        )
    }

    @Test
    @DisplayName("should return 0 when no active refresh tokens exist")
    fun shouldReturnZeroWhenNoActiveTokens() {
        val user = UserEntity().apply { id = testUserId }
        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(refreshTokenRepository.revokeAllByUserId(testUserId)).thenReturn(0)

        val result = authService.revokeAllSessions(testUserId)

        assertEquals(0, result)
        verify(refreshTokenRepository).revokeAllByUserId(testUserId)
        verify(auditLogService).logEvent(
            eq(testUserId),
            eq(AuditAction.SESSION_REVOKED),
            any(), any(), any()
        )
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException for non-existent user")
    fun shouldThrowWhenUserNotFound() {
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<ResourceNotFoundException> {
            authService.revokeAllSessions(999L)
        }

        verify(refreshTokenRepository, never()).revokeAllByUserId(any())
        verify(auditLogService, never()).logEvent(any(), any(), any(), any(), any())
    }
}
