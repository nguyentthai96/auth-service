package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.port.out.TokenStore
import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserId
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Tests for RevokeSessionsHandler (FR-012 — Force Logout).
 * Migrated from AuthServiceRevokeSessionsTest after AuthService removal.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("RevokeSessionsHandler Tests")
class RevokeSessionsHandlerTest {

    @Mock private lateinit var tokenStore: TokenStore
    @Mock private lateinit var userPort: UserPort
    @Mock private lateinit var auditLogService: AuditLogService

    private lateinit var handler: RevokeSessionsHandler

    private val testUserId = 42L

    @BeforeEach
    fun setUp() {
        handler = RevokeSessionsHandler(tokenStore, userPort, auditLogService)
    }

    @Test
    @DisplayName("should revoke all refresh tokens and return count")
    fun shouldRevokeAllRefreshTokensAndReturnCount() {
        val user = User(id = UserId(testUserId), username = "testuser", email = "test@example.com", passwordHash = "hash", status = "ACTIVE")
        whenever(userPort.findById(testUserId)).thenReturn(user)
        whenever(tokenStore.revokeAllForUser(testUserId)).thenReturn(5)

        val result = handler.handle(RevokeSessionsCommand(testUserId))

        assertEquals(5, result)
        verify(tokenStore).revokeAllForUser(testUserId)
        verify(auditLogService).logEvent(
            eq(testUserId),
            eq(AuditAction.FORCE_LOGOUT),
            eq("User"),
            eq(testUserId.toString()),
            argThat { contains("revokedTokens=5") }
        )
    }

    @Test
    @DisplayName("should return 0 when no active refresh tokens exist")
    fun shouldReturnZeroWhenNoActiveTokens() {
        val user = User(id = UserId(testUserId), username = "testuser", email = "test@example.com", passwordHash = "hash", status = "ACTIVE")
        whenever(userPort.findById(testUserId)).thenReturn(user)
        whenever(tokenStore.revokeAllForUser(testUserId)).thenReturn(0)

        val result = handler.handle(RevokeSessionsCommand(testUserId))

        assertEquals(0, result)
        verify(tokenStore).revokeAllForUser(testUserId)
        verify(auditLogService).logEvent(
            eq(testUserId),
            eq(AuditAction.FORCE_LOGOUT),
            any(), any(), any()
        )
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException for non-existent user")
    fun shouldThrowWhenUserNotFound() {
        whenever(userPort.findById(999L)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            handler.handle(RevokeSessionsCommand(999L))
        }

        verify(tokenStore, never()).revokeAllForUser(any())
        verify(auditLogService, never()).logEvent(any(), any(), any(), any(), any())
    }
}
