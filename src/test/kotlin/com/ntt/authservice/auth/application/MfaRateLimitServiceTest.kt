package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.audit.AuditAction
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.MfaAccountLockedException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Unit tests for MfaRateLimitService — verifies rate limiting, locking, unlocking, and fail-open.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("MfaRateLimitService Tests")
class MfaRateLimitServiceTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var auditLogService: AuditLogService
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var rateLimitService: MfaRateLimitService

    @BeforeEach
    fun setUp() {
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        // Setup config: OTP = max 5, 900s window, 1800s lock
        val otpConfig = SecurityProperties.MfaProperties.LimitConfig(
            maxAttempts = 5, windowSeconds = 900, lockSeconds = 1800
        )
        val loginConfig = SecurityProperties.MfaProperties.LimitConfig(
            maxAttempts = 10, windowSeconds = 3600, lockSeconds = 3600
        )
        val rateLimitProps = SecurityProperties.MfaProperties.RateLimitProperties(
            otp = otpConfig, login = loginConfig, redisTimeoutMs = 500
        )
        val mfaProps = SecurityProperties.MfaProperties(rateLimit = rateLimitProps)
        lenient().whenever(securityProperties.mfa).thenReturn(mfaProps)

        rateLimitService = MfaRateLimitService(
            redisTemplate, securityProperties, auditLogService, eventPublisher
        )
    }

    // --- checkAndIncrement tests ---

    @Test
    @DisplayName("should allow request within OTP rate limit")
    fun shouldAllowWithinLimit() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(false)
        whenever(valueOps.increment("mfa:verify:attempts:1:OTP")).thenReturn(1L)

        // Should not throw
        assertDoesNotThrow {
            rateLimitService.checkAndIncrement(1L, RateLimitType.OTP_VERIFY)
        }
    }

    @Test
    @DisplayName("should set TTL on first increment")
    fun shouldSetTtlOnFirstIncrement() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(false)
        whenever(valueOps.increment("mfa:verify:attempts:1:OTP")).thenReturn(1L)

        rateLimitService.checkAndIncrement(1L, RateLimitType.OTP_VERIFY)

        verify(redisTemplate).expire("mfa:verify:attempts:1:OTP", Duration.ofSeconds(900))
    }

    @Test
    @DisplayName("should NOT set TTL on subsequent increments")
    fun shouldNotSetTtlOnSubsequentIncrements() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(false)
        whenever(valueOps.increment("mfa:verify:attempts:1:OTP")).thenReturn(3L)

        rateLimitService.checkAndIncrement(1L, RateLimitType.OTP_VERIFY)

        verify(redisTemplate, never()).expire(any<String>(), any<Duration>())
    }

    @Test
    @DisplayName("should throw MfaAccountLockedException when already locked")
    fun shouldRejectWhenAlreadyLocked() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(true)
        whenever(redisTemplate.getExpire("mfa:verify:lock:1:OTP")).thenReturn(1500L)

        val exception = assertThrows<MfaAccountLockedException> {
            rateLimitService.checkAndIncrement(1L, RateLimitType.OTP_VERIFY)
        }

        assertEquals(1500L, exception.retryAfterSeconds)
        assertEquals("OTP", exception.lockType)
    }

    @Test
    @DisplayName("should lock user after exceeding OTP threshold and publish event")
    fun shouldLockAfterExceedingThreshold() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(false)
        whenever(valueOps.increment("mfa:verify:attempts:1:OTP")).thenReturn(6L)

        val exception = assertThrows<MfaAccountLockedException> {
            rateLimitService.checkAndIncrement(1L, RateLimitType.OTP_VERIFY)
        }

        assertEquals(1800L, exception.retryAfterSeconds)
        assertEquals("OTP", exception.lockType)

        // Verify lock key created
        verify(valueOps).set(eq("mfa:verify:lock:1:OTP"), eq("locked"), eq(Duration.ofSeconds(1800)))

        // Verify audit log
        verify(auditLogService).logEvent(
            userId = eq(1L),
            action = eq(AuditAction.MFA_OTP_LOCKED),
            entityType = eq("User"),
            entityId = eq("1"),
            details = any()
        )

        // Verify event published
        verify(eventPublisher).publishEvent(any())
    }

    @Test
    @DisplayName("should lock MFA login after exceeding threshold")
    fun shouldLockMfaLoginAfterExceedingThreshold() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:MFA_LOGIN")).thenReturn(false)
        whenever(valueOps.increment("mfa:verify:attempts:1:MFA_LOGIN")).thenReturn(11L)

        val exception = assertThrows<MfaAccountLockedException> {
            rateLimitService.checkAndIncrement(1L, RateLimitType.MFA_LOGIN)
        }

        assertEquals(3600L, exception.retryAfterSeconds)
        assertEquals("MFA_LOGIN", exception.lockType)

        verify(auditLogService).logEvent(
            userId = eq(1L),
            action = eq(AuditAction.MFA_LOGIN_LOCKED),
            entityType = eq("User"),
            entityId = eq("1"),
            details = any()
        )
    }

    // --- Fail-open tests ---

    @Test
    @DisplayName("should fail-open when Redis is unavailable")
    fun shouldFailOpenWhenRedisUnavailable() {
        whenever(redisTemplate.hasKey(any())).thenThrow(RedisConnectionFailureException("Connection refused"))

        // Should NOT throw — fail-open allows request through
        assertDoesNotThrow {
            rateLimitService.checkAndIncrement(1L, RateLimitType.OTP_VERIFY)
        }
    }

    // --- isLocked tests ---

    @Test
    @DisplayName("should return true when user is locked")
    fun shouldReturnTrueWhenLocked() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(true)

        assertTrue(rateLimitService.isLocked(1L, RateLimitType.OTP_VERIFY))
    }

    @Test
    @DisplayName("should return false when user is not locked")
    fun shouldReturnFalseWhenNotLocked() {
        whenever(redisTemplate.hasKey("mfa:verify:lock:1:OTP")).thenReturn(false)

        assertFalse(rateLimitService.isLocked(1L, RateLimitType.OTP_VERIFY))
    }

    @Test
    @DisplayName("isLocked should return false when Redis fails")
    fun isLockedShouldReturnFalseOnRedisFailure() {
        whenever(redisTemplate.hasKey(any())).thenThrow(RedisConnectionFailureException("down"))

        assertFalse(rateLimitService.isLocked(1L, RateLimitType.OTP_VERIFY))
    }

    // --- resetCounters tests ---

    @Test
    @DisplayName("should delete all attempt and lock keys on reset")
    fun shouldResetCounters() {
        rateLimitService.resetCounters(1L)

        verify(redisTemplate).delete(argThat<Collection<String>> { keys ->
            keys.containsAll(
                listOf(
                    "mfa:verify:attempts:1:OTP",
                    "mfa:verify:lock:1:OTP",
                    "mfa:verify:attempts:1:MFA_LOGIN",
                    "mfa:verify:lock:1:MFA_LOGIN"
                )
            )
        })
    }

    // --- adminUnlock tests ---

    @Test
    @DisplayName("should delete all keys and audit log on admin unlock")
    fun shouldAdminUnlock() {
        rateLimitService.adminUnlock(1L)

        verify(redisTemplate).delete(any<Collection<String>>())
        verify(auditLogService).logEvent(
            userId = eq(1L),
            action = eq(AuditAction.MFA_ADMIN_UNLOCKED),
            entityType = eq("User"),
            entityId = eq("1"),
            details = any()
        )
    }
}
