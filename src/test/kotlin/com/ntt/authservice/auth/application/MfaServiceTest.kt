package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.repository.MfaRecoveryCodeRepository
import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.audit.AuditLogService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.*

/**
 * Unit tests for MfaService — MFA orchestration, TOTP setup/confirm, resend OTP, settings.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("MfaService Tests")
class MfaServiceTest {

    @Mock private lateinit var otpService: OtpService
    @Mock private lateinit var totpService: TotpService
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var securityProperties: SecurityProperties
    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var auditLogService: AuditLogService
    @Mock private lateinit var rateLimitService: MfaRateLimitService
    @Mock private lateinit var recoveryCodeRepository: MfaRecoveryCodeRepository
    @Mock private lateinit var mfaProperties: SecurityProperties.MfaProperties
    @Mock private lateinit var jwtProperties: SecurityProperties.JwtProperties
    @Mock private lateinit var valueOps: ValueOperations<String, String>
    @Mock private lateinit var mockClaims: Claims

    private lateinit var mfaService: MfaService

    private val mockAuthResponse = AuthResponse(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        tokenType = "Bearer",
        expiresIn = 1800,
        userId = 1L,
        username = "testuser",
        activeDomain = "default",
        roles = emptyList(),
        permissions = emptyList()
    )

    @BeforeEach
    fun setUp() {
        mfaService = MfaService(
            otpService, totpService, jwtService, userRepository,
            securityProperties, redisTemplate, auditLogService, rateLimitService,
            recoveryCodeRepository
        )
    }

    @Test
    @DisplayName("should initiate OTP MFA and return MfaRequired result")
    fun shouldInitiateOtpMfa() {
        whenever(securityProperties.mfa).thenReturn(mfaProperties)
        whenever(mfaProperties.mfaTokenTtlSeconds).thenReturn(300L)
        whenever(otpService.generateOtp(1L, "sms")).thenReturn("123456")
        whenever(jwtService.generateMfaToken(1L, "SMS")).thenReturn("mfa-jwt-token")

        val result = mfaService.initiateMfa(1L, "SMS")

        assertEquals("mfa-jwt-token", result.mfaToken)
        assertEquals("SMS", result.method)
        verify(otpService).generateOtp(1L, "sms")
    }

    @Test
    @DisplayName("should initiate TOTP MFA (stateless — no server action)")
    fun shouldInitiateTotpMfa() {
        whenever(securityProperties.mfa).thenReturn(mfaProperties)
        whenever(mfaProperties.mfaTokenTtlSeconds).thenReturn(300L)
        whenever(jwtService.generateMfaToken(1L, "TOTP")).thenReturn("mfa-jwt-token")

        val result = mfaService.initiateMfa(1L, "TOTP")

        assertEquals("mfa-jwt-token", result.mfaToken)
        assertEquals("TOTP", result.method)
        verifyNoInteractions(otpService)
    }

    @Test
    @DisplayName("should throw for unsupported MFA method")
    fun shouldThrowForUnsupportedMethod() {
        assertThrows<MfaCodeInvalidException> {
            mfaService.initiateMfa(1L, "BIOMETRIC")
        }
    }

    @Test
    @DisplayName("should verify OTP MFA and return AuthResponse")
    fun shouldVerifyOtpMfa() {
        whenever(mockClaims.subject).thenReturn("1")
        whenever(mockClaims.get("method")).thenReturn("SMS")
        whenever(jwtService.parseMfaToken("mfa-token")).thenReturn(mockClaims)

        val result = mfaService.verifyMfa("mfa-token", "123456") { mockAuthResponse }

        assertEquals("access-token", result.accessToken)
        verify(otpService).verifyOtp(1L, "sms", "123456")
        verify(auditLogService).logEvent(eq(1L), any(), any(), any(), any())
    }

    @Test
    @DisplayName("should throw MfaTokenExpiredException for invalid mfaToken")
    fun shouldThrowForInvalidMfaToken() {
        whenever(jwtService.parseMfaToken("bad-token")).thenThrow(RuntimeException("expired"))

        assertThrows<MfaTokenExpiredException> {
            mfaService.verifyMfa("bad-token", "123456") { mockAuthResponse }
        }
    }

    @Test
    @DisplayName("should setup TOTP and store pending secret in Redis")
    fun shouldSetupTotp() {
        val user = UserEntity().apply {
            username = "testuser"
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(totpService.generateSecret()).thenReturn("BASE32SECRET")
        whenever(securityProperties.jwt).thenReturn(jwtProperties)
        whenever(jwtProperties.issuer).thenReturn("auth-service")
        whenever(totpService.generateQrUri("BASE32SECRET", "testuser", "auth-service"))
            .thenReturn("otpauth://totp/auth-service:testuser?secret=BASE32SECRET&issuer=auth-service")
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        val result = mfaService.setupTotp(1L)

        assertEquals("BASE32SECRET", result.secret)
        assertTrue(result.qrCodeUri.contains("otpauth://"))
        verify(valueOps).set(eq("mfa:totp:setup:1"), eq("BASE32SECRET"), eq(Duration.ofMinutes(10)))
        verify(auditLogService).logEvent(any(), any(), any(), any(), any())
    }

    @Test
    @DisplayName("should confirm TOTP and persist encrypted secret")
    fun shouldConfirmTotp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("mfa:totp:setup:1")).thenReturn("BASE32SECRET")
        whenever(totpService.verifyCode("BASE32SECRET", "123456")).thenReturn(true)
        whenever(totpService.encryptSecret("BASE32SECRET")).thenReturn("encrypted-secret")
        val user = UserEntity()
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val result = mfaService.confirmTotp(1L, "123456")

        assertTrue(result)
        assertEquals("encrypted-secret", user.totpSecretEncrypted)
        verify(userRepository).save(user)
        verify(redisTemplate).delete("mfa:totp:setup:1")
        verify(auditLogService).logEvent(any(), any(), any(), any(), any())
    }

    @Test
    @DisplayName("should throw TotpNotSetupException when enabling TOTP without setup")
    fun shouldRejectEnablingTotpWithoutSetup() {
        val user = UserEntity().apply {
            totpSecretEncrypted = null
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))

        assertThrows<TotpNotSetupException> {
            mfaService.updateSettings(1L, true, "TOTP")
        }
    }

    @Test
    @DisplayName("should update MFA settings successfully")
    fun shouldUpdateMfaSettings() {
        val user = UserEntity().apply {
            mfaEnabled = false
            mfaMethod = "NONE"
            totpSecretEncrypted = "encrypted"
        }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val result = mfaService.updateSettings(1L, true, "TOTP")

        assertTrue(result.mfaEnabled)
        assertEquals("TOTP", result.mfaMethod)
        verify(userRepository).save(user)
        verify(auditLogService).logEvent(any(), any(), any(), any(), any())
    }

    @Test
    @DisplayName("should resend OTP and return new MFA token")
    fun shouldResendOtp() {
        whenever(mockClaims.subject).thenReturn("1")
        whenever(mockClaims.get("method")).thenReturn("SMS")
        whenever(jwtService.parseMfaToken("mfa-token")).thenReturn(mockClaims)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment("mfa:resend:1")).thenReturn(1L)
        whenever(securityProperties.mfa).thenReturn(mfaProperties)
        whenever(mfaProperties.mfaTokenTtlSeconds).thenReturn(300L)
        whenever(otpService.generateOtp(1L, "sms")).thenReturn("654321")
        whenever(jwtService.generateMfaToken(1L, "SMS")).thenReturn("new-mfa-token")

        val result = mfaService.resendOtp("mfa-token")

        assertEquals("new-mfa-token", result.mfaToken)
        assertEquals("SMS", result.method)
        verify(otpService).generateOtp(1L, "sms")
    }

    @Test
    @DisplayName("should verify TOTP MFA and return AuthResponse")
    fun shouldVerifyTotpMfa() {
        val user = com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity().apply {
            totpSecretEncrypted = "encrypted-secret"
        }
        whenever(mockClaims.subject).thenReturn("1")
        whenever(mockClaims.get("method")).thenReturn("TOTP")
        whenever(jwtService.parseMfaToken("mfa-token")).thenReturn(mockClaims)
        whenever(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user))
        whenever(totpService.decryptSecret("encrypted-secret")).thenReturn("decoded-secret")
        whenever(totpService.verifyCode("decoded-secret", "123456")).thenReturn(true)

        val result = mfaService.verifyMfa("mfa-token", "123456") { mockAuthResponse }

        assertEquals("access-token", result.accessToken)
        verify(totpService).verifyCode("decoded-secret", "123456")
        verify(auditLogService).logEvent(eq(1L), any(), any(), any(), any())
    }
}
