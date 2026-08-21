package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.`in`.web.dto.AuthResponse
import com.ntt.authservice.auth.application.*
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

/**
 * Integration-style tests for TOTP setup flow (FR-002, FR-003).
 * Tests: setup → get secret+qrUri → confirm → MFA enabled → verify with TOTP.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("TOTP Setup Flow Integration Tests")
class TotpSetupFlowIntegrationTest {

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
    private lateinit var securityProperties: SecurityProperties

    private val testUserId = 1L

    private val mockAuthResponse = AuthResponse(
        accessToken = "totp-access-token",
        refreshToken = "totp-refresh-token",
        tokenType = "Bearer",
        expiresIn = 900,
        userId = testUserId,
        username = "totpuser",
        activeDomain = "default",
        roles = listOf("USER"),
        permissions = emptyList()
    )

    @BeforeEach
    fun setUp() {
        securityProperties = SecurityProperties(
            jwt = SecurityProperties.JwtProperties(issuer = "auth-service-test"),
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

    // ── TC1: Setup → get secret+qrUri → confirm with valid code → MFA enabled ──

    @Test
    @DisplayName("TC1: Full TOTP setup flow — generate secret → confirm → TOTP saved")
    fun shouldCompleteTotpSetupFlow() {
        val user = UserEntity().apply {
            id = testUserId
            username = "totpuser"
            totpSecretEncrypted = null
        }
        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(totpService.generateSecret()).thenReturn("BASE32SECRET")
        whenever(totpService.generateQrUri("BASE32SECRET", "totpuser", "auth-service-test"))
            .thenReturn("otpauth://totp/auth-service-test:totpuser?secret=BASE32SECRET&issuer=auth-service-test")
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        // Step 1: Setup
        val setupResult = mfaService.setupTotp(testUserId)

        assertEquals("BASE32SECRET", setupResult.secret)
        assertTrue(setupResult.qrCodeUri.contains("otpauth://totp/"))
        assertEquals("auth-service-test", setupResult.issuer)
        verify(valueOps).set(eq("mfa:totp:setup:$testUserId"), eq("BASE32SECRET"), any<java.time.Duration>())

        // Step 2: Confirm
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("mfa:totp:setup:$testUserId")).thenReturn("BASE32SECRET")
        whenever(totpService.verifyCode("BASE32SECRET", "123456")).thenReturn(true)
        whenever(totpService.encryptSecret("BASE32SECRET")).thenReturn("encrypted-secret")
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }
        whenever(redisTemplate.delete("mfa:totp:setup:$testUserId")).thenReturn(true)

        val confirmed = mfaService.confirmTotp(testUserId, "123456")

        assertTrue(confirmed)
        assertEquals("encrypted-secret", user.totpSecretEncrypted)
        verify(redisTemplate).delete("mfa:totp:setup:$testUserId")
    }

    // ── TC2: Setup → confirm with wrong code → fail, TOTP not saved ──

    @Test
    @DisplayName("TC2: TOTP confirm with wrong code should fail — secret not persisted")
    fun shouldRejectWrongTotpConfirmationCode() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("mfa:totp:setup:$testUserId")).thenReturn("BASE32SECRET")
        whenever(totpService.verifyCode("BASE32SECRET", "000000")).thenReturn(false)

        assertThrows<MfaCodeInvalidException> {
            mfaService.confirmTotp(testUserId, "000000")
        }

        verify(userRepository, never()).save(any())
    }

    // ── TC3: Setup → wait for Redis TTL expiry → confirm → 400 TOTP_NOT_SETUP ──

    @Test
    @DisplayName("TC3: TOTP confirm after Redis TTL expiry should throw TotpNotSetupException")
    fun shouldRejectConfirmAfterRedisExpiry() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("mfa:totp:setup:$testUserId")).thenReturn(null)

        assertThrows<TotpNotSetupException> {
            mfaService.confirmTotp(testUserId, "123456")
        }
    }

    // ── TC4: Enable MFA method=TOTP without setup → 400 TOTP_NOT_SETUP ──

    @Test
    @DisplayName("TC4: Enable MFA with TOTP method without prior setup should throw TotpNotSetupException")
    fun shouldRejectEnableTotpWithoutSetup() {
        val user = UserEntity().apply {
            id = testUserId
            totpSecretEncrypted = null
            mfaEnabled = false
            mfaMethod = "NONE"
        }
        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))

        assertThrows<TotpNotSetupException> {
            mfaService.updateSettings(testUserId, enabled = true, method = "TOTP")
        }
    }

    // ── TC5: Login → MFA with TOTP → verify with authenticator code → tokens ──

    @Test
    @DisplayName("TC5: Login with TOTP MFA → verify with valid TOTP code → AuthResponse")
    fun shouldVerifyTotpMfaSuccessfully() {
        val user = UserEntity().apply {
            id = testUserId
            totpSecretEncrypted = "encrypted-secret"
            mfaEnabled = true
            mfaMethod = "TOTP"
        }
        whenever(jwtService.parseMfaToken("mfa-token")).thenReturn(mockClaims)
        whenever(mockClaims.subject).thenReturn(testUserId.toString())
        whenever(mockClaims["method"]).thenReturn("TOTP")
        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(totpService.decryptSecret("encrypted-secret")).thenReturn("BASE32SECRET")
        whenever(totpService.verifyCode("BASE32SECRET", "654321")).thenReturn(true)

        val response = mfaService.verifyMfa(
            mfaToken = "mfa-token",
            code = "654321",
            authResponseBuilder = { mockAuthResponse }
        )

        assertEquals("totp-access-token", response.accessToken)
        verify(rateLimitService).resetCounters(testUserId)
        verify(auditLogService).logEvent(
            eq(testUserId),
            eq(com.ntt.authservice.shared.audit.AuditAction.MFA_VERIFY_SUCCESS),
            any(), any(), any()
        )
    }

    // ── TC6: TOTP verify with invalid code → MFA_VERIFY_FAILED audit ──

    @Test
    @DisplayName("TC6: TOTP verify with invalid code should throw and log MFA_VERIFY_FAILED")
    fun shouldRejectInvalidTotpCode() {
        val user = UserEntity().apply {
            id = testUserId
            totpSecretEncrypted = "encrypted-secret"
        }
        whenever(jwtService.parseMfaToken("mfa-token")).thenReturn(mockClaims)
        whenever(mockClaims.subject).thenReturn(testUserId.toString())
        whenever(mockClaims["method"]).thenReturn("TOTP")
        whenever(userRepository.findById(testUserId)).thenReturn(Optional.of(user))
        whenever(totpService.decryptSecret("encrypted-secret")).thenReturn("BASE32SECRET")
        whenever(totpService.verifyCode("BASE32SECRET", "000000")).thenReturn(false)

        assertThrows<MfaCodeInvalidException> {
            mfaService.verifyMfa(
                mfaToken = "mfa-token",
                code = "000000",
                authResponseBuilder = { mockAuthResponse }
            )
        }

        verify(auditLogService).logEvent(
            eq(testUserId),
            eq(com.ntt.authservice.shared.audit.AuditAction.MFA_VERIFY_FAILED),
            any(), any(), any()
        )
    }
}
