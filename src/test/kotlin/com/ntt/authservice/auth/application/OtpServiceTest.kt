package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.MfaCodeInvalidException
import com.ntt.authservice.shared.exception.MfaMaxAttemptsException
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
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

/**
 * Unit tests for OtpService — verifies OTP generation, verification, and rate limiting.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("OtpService Tests")
class OtpServiceTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var mfaProperties: SecurityProperties.MfaProperties
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var otpService: OtpService

    @BeforeEach
    fun setUp() {
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        lenient().whenever(securityProperties.mfa).thenReturn(mfaProperties)
        lenient().whenever(mfaProperties.otpTtlSeconds).thenReturn(300L)
        lenient().whenever(mfaProperties.maxAttempts).thenReturn(3)
        otpService = OtpService(redisTemplate, securityProperties)
    }

    @Test
    @DisplayName("should generate 6-digit OTP and store in Redis")
    fun shouldGenerateOtp() {
        whenever(redisTemplate.hasKey(any())).thenReturn(false)

        val code = otpService.generateOtp(1L, "sms")

        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
        verify(valueOps).set(eq("otp:1:sms"), eq(code), eq(Duration.ofSeconds(300)))
        verify(valueOps).set(eq("otp:1:sms:attempts"), eq("0"), eq(Duration.ofSeconds(300)))
    }

    @Test
    @DisplayName("should rate limit OTP generation — reject within 60s")
    fun shouldRateLimitOtpGeneration() {
        whenever(redisTemplate.hasKey("otp:ratelimit:1:sms")).thenReturn(true)

        assertThrows<MfaCodeInvalidException> {
            otpService.generateOtp(1L, "sms")
        }
    }

    @Test
    @DisplayName("should verify correct OTP and delete keys")
    fun shouldVerifyCorrectOtp() {
        whenever(valueOps.get("otp:1:sms")).thenReturn("123456")

        val result = otpService.verifyOtp(1L, "sms", "123456")

        assertTrue(result)
        verify(redisTemplate).delete(listOf("otp:1:sms", "otp:1:sms:attempts"))
    }

    @Test
    @DisplayName("should throw MfaCodeInvalidException for wrong OTP")
    fun shouldRejectWrongOtp() {
        whenever(valueOps.get("otp:1:sms")).thenReturn("123456")
        whenever(valueOps.increment("otp:1:sms:attempts")).thenReturn(1L)

        assertThrows<MfaCodeInvalidException> {
            otpService.verifyOtp(1L, "sms", "000000")
        }
    }

    @Test
    @DisplayName("should throw MfaMaxAttemptsException after max attempts")
    fun shouldLockAfterMaxAttempts() {
        whenever(valueOps.get("otp:1:sms")).thenReturn("123456")
        whenever(valueOps.increment("otp:1:sms:attempts")).thenReturn(3L)

        assertThrows<MfaMaxAttemptsException> {
            otpService.verifyOtp(1L, "sms", "000000")
        }
        verify(redisTemplate).delete(listOf("otp:1:sms", "otp:1:sms:attempts"))
    }

    @Test
    @DisplayName("should throw MfaCodeInvalidException for expired OTP")
    fun shouldRejectExpiredOtp() {
        whenever(valueOps.get("otp:1:sms")).thenReturn(null)

        assertThrows<MfaCodeInvalidException> {
            otpService.verifyOtp(1L, "sms", "123456")
        }
    }
}
