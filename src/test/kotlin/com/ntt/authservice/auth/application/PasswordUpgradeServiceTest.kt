package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Unit tests for PasswordUpgradeService.
 * Verifies rehash-on-login behavior, audit logging, and Micrometer counter.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("PasswordUpgradeService")
class PasswordUpgradeServiceTest {

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var auditLogService: AuditLogService

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var service: PasswordUpgradeService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        service = PasswordUpgradeService(passwordEncoder, auditLogService, meterRegistry)
    }

    private fun createUser(hash: String = "{bcrypt}\$2a\$12\$dummy"): User {
        return User(
            id = UserId(42L),
            username = "testuser",
            email = Email("test@example.com"),
            fullName = "Test User",
            passwordHash = PasswordHash(hash),
            status = UserStatus.Active
        )
    }

    @Test
    @DisplayName("FR-003: BCrypt hash → upgrade to Argon2id")
    fun `upgrades password when encoder signals upgrade needed`() {
        val user = createUser("{bcrypt}\$2a\$12\$old")
        val newHash = "{argon2id}\$argon2id\$v=19\$m=65536,t=3,p=1\$salt\$hash"

        whenever(passwordEncoder.upgradeEncoding("{bcrypt}\$2a\$12\$old")).thenReturn(true)
        whenever(passwordEncoder.encode("rawPassword")).thenReturn(newHash)

        service.upgradeIfNeeded(user, "rawPassword")

        assertEquals(newHash, user.passwordHash.value)
    }

    @Test
    @DisplayName("FR-003: Argon2id hash → no upgrade needed")
    fun `does not upgrade when encoder signals no upgrade needed`() {
        val originalHash = "{argon2id}\$argon2id\$v=19\$m=65536,t=3,p=1\$salt\$hash"
        val user = createUser(originalHash)

        whenever(passwordEncoder.upgradeEncoding(originalHash)).thenReturn(false)

        service.upgradeIfNeeded(user, "rawPassword")

        assertEquals(originalHash, user.passwordHash.value)
        verify(passwordEncoder, never()).encode(any())
    }

    @Test
    @DisplayName("FR-011: Audit event recorded on upgrade")
    fun `records audit event when password is rehashed`() {
        val user = createUser("{bcrypt}\$2a\$12\$old")
        val newHash = "{argon2id}\$argon2id\$v=19\$new"

        whenever(passwordEncoder.upgradeEncoding(any())).thenReturn(true)
        whenever(passwordEncoder.encode(any<String>())).thenReturn(newHash)

        service.upgradeIfNeeded(user, "rawPassword")

        verify(auditLogService).logEvent(
            eq(42L),
            eq(AuditAction.PASSWORD_REHASHED),
            eq("User"),
            eq("42"),
            eq("algorithm=argon2id")
        )
    }

    @Test
    @DisplayName("FR-011: No audit event when no upgrade needed")
    fun `does not record audit event when no upgrade needed`() {
        val user = createUser("{argon2id}\$argon2id\$current")

        whenever(passwordEncoder.upgradeEncoding(any())).thenReturn(false)

        service.upgradeIfNeeded(user, "rawPassword")

        verify(auditLogService, never()).logEvent(any(), any(), any(), any(), any())
    }

    @Test
    @DisplayName("FR-012: Micrometer counter incremented on upgrade")
    fun `increments rehash counter when password is upgraded`() {
        val user = createUser("{bcrypt}\$2a\$12\$old")

        whenever(passwordEncoder.upgradeEncoding(any())).thenReturn(true)
        whenever(passwordEncoder.encode(any<String>())).thenReturn("{argon2id}\$new")

        service.upgradeIfNeeded(user, "rawPassword")

        val counter = meterRegistry.find("password.migration.rehash.total").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    @DisplayName("FR-012: Counter not incremented when no upgrade")
    fun `does not increment counter when no upgrade needed`() {
        val user = createUser("{argon2id}\$current")

        whenever(passwordEncoder.upgradeEncoding(any())).thenReturn(false)

        service.upgradeIfNeeded(user, "rawPassword")

        val counter = meterRegistry.find("password.migration.rehash.total").counter()
        assertNotNull(counter)
        assertEquals(0.0, counter!!.count())
    }
}
