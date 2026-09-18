package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.AltchaChallenge
import com.ntt.authservice.auth.application.AltchaCaptchaVerifier
import com.ntt.authservice.auth.application.ImageCaptchaChallenge
import com.ntt.authservice.auth.application.ImageCaptchaStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

/**
 * Unit tests for CaptchaController — CAPTCHA challenge endpoint.
 * Covers: dual CAPTCHA type dispatch (altcha, image), default type, input validation.
 *
 * FR-014: Dual CAPTCHA Strategy
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("CaptchaController Tests")
class CaptchaControllerTest {

    @Mock
    private lateinit var altchaCaptchaVerifier: AltchaCaptchaVerifier

    @Mock
    private lateinit var imageCaptchaStrategy: ImageCaptchaStrategy

    @InjectMocks
    private lateinit var controller: CaptchaController

    private val testAltchaChallenge = AltchaChallenge(
        algorithm = "SHA-256",
        challenge = "abc123hash",
        salt = "test-salt-uuid",
        signature = "hmac-sig",
        maxnumber = 50000
    )

    private val testImageChallenge = ImageCaptchaChallenge(
        captchaId = "img-uuid-123",
        imageBase64 = "iVBORw0KGgoAAAANSUhEU...",
        ttlSeconds = 180
    )

    @Nested
    @DisplayName("GET /captcha/challenge — type dispatch")
    inner class ChallengeTypeDispatch {

        @Test
        @DisplayName("TC1: type=altcha → returns Altcha PoW challenge")
        fun shouldReturnAltchaChallengeWhenTypeAltcha() {
            // Given
            whenever(altchaCaptchaVerifier.generateChallenge()).thenReturn(testAltchaChallenge)

            // When
            val response = controller.getChallenge("altcha")

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as AltchaChallenge
            assertEquals("SHA-256", body.algorithm)
            assertEquals("abc123hash", body.challenge)
            assertEquals("test-salt-uuid", body.salt)
            assertEquals("hmac-sig", body.signature)
            assertEquals(50000, body.maxnumber)
        }

        @Test
        @DisplayName("TC2: type=image → returns Image CAPTCHA challenge")
        fun shouldReturnImageChallengeWhenTypeImage() {
            // Given
            whenever(imageCaptchaStrategy.generateChallenge()).thenReturn(testImageChallenge)

            // When
            val response = controller.getChallenge("image")

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as ImageCaptchaChallenge
            assertEquals("img-uuid-123", body.captchaId)
            assertTrue(body.imageBase64.isNotBlank())
            assertEquals(180, body.ttlSeconds)
        }

        @Test
        @DisplayName("TC3: no type param (default) → returns Altcha challenge")
        fun shouldReturnAltchaChallengeByDefault() {
            // Given
            whenever(altchaCaptchaVerifier.generateChallenge()).thenReturn(testAltchaChallenge)

            // When — default parameter value is "altcha"
            val response = controller.getChallenge("altcha")

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body is AltchaChallenge)
        }

        @Test
        @DisplayName("TC4: type=IMAGE (case insensitive) → returns Image challenge")
        fun shouldHandleCaseInsensitiveType() {
            // Given
            whenever(imageCaptchaStrategy.generateChallenge()).thenReturn(testImageChallenge)

            // When
            val response = controller.getChallenge("IMAGE")

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body is ImageCaptchaChallenge)
        }

        @Test
        @DisplayName("TC5: type=unknown → falls back to Altcha (default)")
        fun shouldFallBackToAltchaForUnknownType() {
            // Given
            whenever(altchaCaptchaVerifier.generateChallenge()).thenReturn(testAltchaChallenge)

            // When
            val response = controller.getChallenge("recaptcha")

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body is AltchaChallenge)
        }
    }
}
