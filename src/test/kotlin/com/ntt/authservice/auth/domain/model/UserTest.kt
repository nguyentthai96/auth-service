package com.ntt.authservice.auth.domain.model

import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * Domain model unit tests — pure Kotlin, NO Spring context.
 * Tests value objects, sealed types, and domain logic.
 */
class UserTest {

    @Nested
    @DisplayName("UserId Value Object")
    inner class UserIdTests {
        @Test
        fun `should create valid UserId`() {
            val id = UserId(123L)
            assertEquals(123L, id.value)
        }

        @Test
        fun `should allow zero for new entities`() {
            val id = UserId(0L)
            assertEquals(0L, id.value)
            assertTrue(id.isNew)
        }

        @Test
        fun `should reject negative UserId`() {
            assertThrows<IllegalArgumentException> { UserId(-1L) }
        }
    }

    @Nested
    @DisplayName("Email Value Object")
    inner class EmailTests {
        @Test
        fun `should create valid Email`() {
            val email = Email("user@example.com")
            assertEquals("user@example.com", email.value)
        }

        @Test
        fun `should reject Email without at sign`() {
            assertThrows<IllegalArgumentException> { Email("invalid-email") }
        }

        @Test
        fun `should reject blank Email`() {
            assertThrows<IllegalArgumentException> { Email("") }
        }
    }

    @Nested
    @DisplayName("UserStatus Sealed Interface")
    inner class UserStatusTests {
        @Test
        fun `should convert Active string to sealed type`() {
            val status = UserStatus.fromString("ACTIVE")
            assertEquals(UserStatus.Active, status)
        }

        @Test
        fun `should convert Locked string to sealed type`() {
            val until = Instant.now().plusSeconds(3600)
            val status = UserStatus.fromString("LOCKED", until)
            assertTrue(status is UserStatus.Locked)
            assertEquals(until, (status as UserStatus.Locked).until)
        }

        @Test
        fun `should convert sealed type back to string`() {
            assertEquals("ACTIVE", UserStatus.toDbString(UserStatus.Active))
            assertEquals("LOCKED", UserStatus.toDbString(UserStatus.Locked(Instant.now())))
            assertEquals("SUSPENDED", UserStatus.toDbString(UserStatus.Suspended))
        }

        @Test
        fun `should reject unknown status string`() {
            assertThrows<IllegalArgumentException> { UserStatus.fromString("UNKNOWN") }
        }
    }

    @Nested
    @DisplayName("User Domain Model")
    inner class UserDomainTests {
        private fun createUser(
            status: UserStatus = UserStatus.Active,
            failedLoginCount: Int = 0,
            mfaEnabled: Boolean = false
        ) = User(
            id = UserId(1L),
            username = "testuser",
            email = Email("test@example.com"),
            passwordHash = PasswordHash("\$2a\$10\$encoded"),
            fullName = "Test User",
            status = status,
            failedLoginCount = failedLoginCount,
            mfaEnabled = mfaEnabled,
            mfaMethod = if (mfaEnabled) "TOTP" else "NONE"
        )

        @Test
        fun `should detect expired lock`() {
            val pastLock = Instant.now().minusSeconds(3600)
            val user = createUser(status = UserStatus.Locked(pastLock))
            assertTrue(user.isLockExpired())
        }

        @Test
        fun `should not detect active lock as expired`() {
            val futureLock = Instant.now().plusSeconds(3600)
            val user = createUser(status = UserStatus.Locked(futureLock))
            assertFalse(user.isLockExpired())
        }

        @Test
        fun `should unlock when lock expired`() {
            val pastLock = Instant.now().minusSeconds(3600)
            val user = createUser(status = UserStatus.Locked(pastLock), failedLoginCount = 5)
            user.unlockIfExpired()
            assertEquals(UserStatus.Active, user.status)
            assertEquals(0, user.failedLoginCount)
        }

        @Test
        fun `should record failed login and lock at threshold`() {
            val user = createUser(failedLoginCount = 4)
            user.recordFailedLogin(maxAttempts = 5, lockDurationSeconds = 300)
            assertTrue(user.status is UserStatus.Locked)
            assertNotNull(user.lockedUntilAt)
        }

        @Test
        fun `should increment count without lock below threshold`() {
            val user = createUser(failedLoginCount = 2)
            user.recordFailedLogin(maxAttempts = 5, lockDurationSeconds = 300)
            assertEquals(3, user.failedLoginCount)
            assertEquals(UserStatus.Active, user.status)
        }

        @Test
        fun `should reset failed logins`() {
            val user = createUser(failedLoginCount = 3)
            user.resetFailedLogins()
            assertEquals(0, user.failedLoginCount)
        }

        @Test
        fun `should require MFA when enabled and device not trusted`() {
            val user = createUser(mfaEnabled = true)
            assertTrue(user.requiresMfa(null))
            assertTrue(user.requiresMfa("wrong-hash"))
        }

        @Test
        fun `should skip MFA when device is trusted`() {
            val user = User(
                id = UserId(1L),
                username = "testuser",
                email = Email("test@example.com"),
                passwordHash = PasswordHash("\$2a\$10\$encoded"),
                fullName = "Test User",
                mfaEnabled = true,
                mfaMethod = "TOTP",
                trustedDeviceHash = "trusted-hash-123"
            )
            assertFalse(user.requiresMfa("trusted-hash-123"))
        }

        @Test
        fun `should not require MFA when disabled`() {
            val user = createUser(mfaEnabled = false)
            assertFalse(user.requiresMfa(null))
        }
    }
}
