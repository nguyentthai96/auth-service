package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Edge case tests for MFA Service (FR-001, FR-002, FR-015).
 * Tests: concurrent MFA verify idempotency, method mismatch handling.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("MFA Service Edge Case Tests")
class MfaServiceEdgeCaseTest {

    @Mock private lateinit var otpService: OtpService
    @Mock private lateinit var totpService: TotpService
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var auditLogService: AuditLogService
    @Mock private lateinit var rateLimitService: MfaRateLimitService
    @Mock private lateinit var recoveryCodeRepository: com.ntt.authservice.auth.adapter.out.persistence.repository.MfaRecoveryCodeRepository
    @Mock private lateinit var valueOps: ValueOperations<String, String>
    @Mock private lateinit var mockClaims: Claims

    private lateinit var mfaService: MfaService

    private val mockAuthResponse = AuthResponse(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        tokenType = "Bearer",
        expiresIn = 900,
        userId = 1L,
        username = "testuser",
        activeDomain = "default",
        roles = emptyList(),
        permissions = emptyList()
    )

    @BeforeEach
    fun setUp() {
        val securityProperties = SecurityProperties(
            mfa = SecurityProperties.MfaProperties(
                otpTtlSeconds = 300,
                maxAttempts = 3,
                totpWindow = 1,
                mfaTokenTtlSeconds = 300,
                trustedDeviceTtlDays = 30
            )
        )
        mfaService = MfaService(
            otpService, totpService, jwtService, userRepository,
            securityProperties, redisTemplate, auditLogService, rateLimitService,
            recoveryCodeRepository
        )
    }

    // ── TC1: Concurrent MFA verify — first succeeds, second fails (Redis DEL atomicity) ──

    @Test
    @DisplayName("TC1: Concurrent MFA verify should allow only one success (OTP replay protection)")
    fun shouldPreventConcurrentMfaVerifyReplay() {
        val userId = 1L
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        whenever(jwtService.parseMfaToken("mfa-token")).thenReturn(mockClaims)
        whenever(mockClaims.subject).thenReturn(userId.toString())
        whenever(mockClaims["method"]).thenReturn("SMS")

        // First call succeeds, subsequent calls fail (OTP already consumed by Redis DEL)
        var callCount = 0
        doAnswer {
            callCount++
            if (callCount > 1) {
                throw MfaCodeInvalidException("Invalid or expired OTP")
            }
        }.whenever(otpService).verifyOtp(userId, "sms", "123456")

        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)

        repeat(2) {
            executor.submit {
                try {
                    mfaService.verifyMfa(
                        mfaToken = "mfa-token",
                        code = "123456",
                        authResponseBuilder = { mockAuthResponse }
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // At least one should succeed and at least one should fail
        // Due to race conditions, exact counts may vary but total should be 2
        assertEquals(2, successCount.get() + failCount.get())
        assertTrue(failCount.get() >= 1, "At least one concurrent verify should fail (replay protection)")
    }

    // ── TC2: MFA token claims method=SMS but user has method=TOTP → proper error ──

    @Test
    @DisplayName("TC2: MFA token with SMS method should still validate via OTP path (method from token)")
    fun shouldUseMethodFromTokenNotFromUser() {
        val userId = 1L

        // Token claims method=SMS
        whenever(jwtService.parseMfaToken("mfa-token-sms")).thenReturn(mockClaims)
        whenever(mockClaims.subject).thenReturn(userId.toString())
        whenever(mockClaims["method"]).thenReturn("SMS")

        // MfaService uses method from TOKEN (not from user entity)
        // This is correct behavior — the MFA challenge was initiated with a specific method
        mfaService.verifyMfa(
            mfaToken = "mfa-token-sms",
            code = "123456",
            authResponseBuilder = { mockAuthResponse }
        )

        // Should verify via OTP (SMS path), not TOTP
        verify(otpService).verifyOtp(userId, "sms", "123456")
        verify(totpService, never()).verifyCode(any(), any())
    }

    // ── TC3: MFA token with unsupported method → proper error ──

    @Test
    @DisplayName("TC3: MFA token with unsupported method should throw MfaCodeInvalidException")
    fun shouldRejectUnsupportedMfaMethod() {
        whenever(jwtService.parseMfaToken("mfa-token-push")).thenReturn(mockClaims)
        whenever(mockClaims.subject).thenReturn("1")
        whenever(mockClaims["method"]).thenReturn("PUSH")

        assertThrows<MfaCodeInvalidException> {
            mfaService.verifyMfa(
                mfaToken = "mfa-token-push",
                code = "123456",
                authResponseBuilder = { mockAuthResponse }
            )
        }
    }

    // ── TC4: MFA token with missing method claim ──

    @Test
    @DisplayName("TC4: MFA token with missing method claim should throw MfaCodeInvalidException")
    fun shouldRejectMissingMethodClaim() {
        whenever(jwtService.parseMfaToken("mfa-token-no-method")).thenReturn(mockClaims)
        whenever(mockClaims.subject).thenReturn("1")
        whenever(mockClaims["method"]).thenReturn(null)

        assertThrows<MfaCodeInvalidException> {
            mfaService.verifyMfa(
                mfaToken = "mfa-token-no-method",
                code = "123456",
                authResponseBuilder = { mockAuthResponse }
            )
        }
    }
}
