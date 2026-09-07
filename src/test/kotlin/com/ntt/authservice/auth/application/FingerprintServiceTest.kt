package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class FingerprintServiceTest {

    private lateinit var fingerprintService: FingerprintService
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        securityProperties = mock(SecurityProperties::class.java)
        val fpProps = SecurityProperties.FingerprintProperties(
            enabled = true,
            validationEnabled = true,
            strictMode = false,
            headerName = "X-Device-Fingerprint",
            serverComputeFallback = true
        )
        `when`(securityProperties.fingerprint).thenReturn(fpProps)
        fingerprintService = FingerprintService(securityProperties)
    }

    @Test
    fun `resolveFingerprint returns client header when present`() {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.getHeader("X-Device-Fingerprint")).thenReturn("client-fp-abc123")

        val result = fingerprintService.resolveFingerprint(request)

        assertEquals("client-fp-abc123", result)
    }

    @Test
    fun `resolveFingerprint falls back to server-computed when header missing`() {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.getHeader("X-Device-Fingerprint")).thenReturn(null)
        `when`(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Chrome")
        `when`(request.getHeader("Accept-Language")).thenReturn("en-US")
        `when`(request.remoteAddr).thenReturn("192.168.1.1")

        val result = fingerprintService.resolveFingerprint(request)

        assertNotNull(result)
        assertTrue(result!!.length == 64) // SHA-256 hex length
    }

    @Test
    fun `resolveFingerprint returns null when fallback disabled and no header`() {
        val fpProps = SecurityProperties.FingerprintProperties(
            enabled = true,
            validationEnabled = true,
            strictMode = false,
            headerName = "X-Device-Fingerprint",
            serverComputeFallback = false
        )
        `when`(securityProperties.fingerprint).thenReturn(fpProps)
        fingerprintService = FingerprintService(securityProperties)

        val request = mock(HttpServletRequest::class.java)
        `when`(request.getHeader("X-Device-Fingerprint")).thenReturn(null)

        val result = fingerprintService.resolveFingerprint(request)

        assertNull(result)
    }

    @Test
    fun `validateFingerprint returns true for matching fingerprints`() {
        assertTrue(fingerprintService.validateFingerprint("abc123", "abc123"))
    }

    @Test
    fun `validateFingerprint returns false for mismatched fingerprints`() {
        assertFalse(fingerprintService.validateFingerprint("abc123", "xyz789"))
    }

    @Test
    fun `validateFingerprint returns true when actual is null`() {
        // Lenient: if we can't compute fingerprint, don't block
        assertTrue(fingerprintService.validateFingerprint("abc123", null))
    }

    @Test
    fun `server-computed fingerprint is deterministic for same inputs`() {
        val request1 = mock(HttpServletRequest::class.java)
        `when`(request1.getHeader("X-Device-Fingerprint")).thenReturn(null)
        `when`(request1.getHeader("User-Agent")).thenReturn("Mozilla/5.0")
        `when`(request1.getHeader("Accept-Language")).thenReturn("en")
        `when`(request1.remoteAddr).thenReturn("10.0.0.1")

        val request2 = mock(HttpServletRequest::class.java)
        `when`(request2.getHeader("X-Device-Fingerprint")).thenReturn(null)
        `when`(request2.getHeader("User-Agent")).thenReturn("Mozilla/5.0")
        `when`(request2.getHeader("Accept-Language")).thenReturn("en")
        `when`(request2.remoteAddr).thenReturn("10.0.0.1")

        val fp1 = fingerprintService.resolveFingerprint(request1)
        val fp2 = fingerprintService.resolveFingerprint(request2)

        assertEquals(fp1, fp2)
    }
}
