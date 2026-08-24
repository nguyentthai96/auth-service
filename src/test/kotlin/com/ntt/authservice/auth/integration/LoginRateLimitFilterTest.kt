package com.ntt.authservice.auth.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.authservice.auth.application.LoginRateLimitService
import com.ntt.authservice.shared.exception.RateLimitExceededException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.context.MessageSource
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import com.ntt.authservice.auth.adapter.`in`.web.filter.LoginRateLimitFilter
import java.util.Locale

/**
 * Unit tests for LoginRateLimitFilter — pre-auth rate limiting.
 * Tests IP-based and username-based rate limiting, fail-open strategy.
 *
 * FR-004: Login security (rate limiting aspect)
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("LoginRateLimitFilter Tests")
class LoginRateLimitFilterTest {

    @Mock private lateinit var loginRateLimitService: LoginRateLimitService
    @Mock private lateinit var messageSource: MessageSource

    private lateinit var filter: LoginRateLimitFilter
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        filter = LoginRateLimitFilter(loginRateLimitService, objectMapper, messageSource)
    }

    @Test
    @DisplayName("TC1: Under rate limit → login proceeds normally")
    fun shouldProceedWhenUnderLimit() {
        // Given — rate limit check passes
        doNothing().whenever(loginRateLimitService).checkMultiDimensional(any(), any(), anyOrNull())

        val request = MockHttpServletRequest("POST", "/api/auth/login")
        request.remoteAddr = "192.168.1.1"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // When
        filter.doFilter(request, response, chain)

        // Then — filter chain called (request proceeds)
        assertNotNull(chain.request)
    }

    @Test
    @DisplayName("TC2: Exceed IP rate limit → 429 AUTH_020")
    fun shouldReturn429WhenIpRateLimited() {
        // Given — rate limit exceeded
        doThrow(RateLimitExceededException(retryAfterSeconds = 300, dimension = "ip"))
            .whenever(loginRateLimitService).checkMultiDimensional(any(), any(), anyOrNull())
        whenever(messageSource.getMessage(any<String>(), any(), any<String>(), any<Locale>()))
            .thenReturn("Too many login attempts")

        val request = MockHttpServletRequest("POST", "/api/auth/login")
        request.remoteAddr = "192.168.1.1"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // When
        filter.doFilter(request, response, chain)

        // Then
        assertEquals(429, response.status)
        assertNull(chain.request, "Filter chain should NOT be called")
        assertTrue(response.contentAsString.contains("AUTH_020"))
        assertEquals("300", response.getHeader("Retry-After"))
    }

    @Test
    @DisplayName("TC3: Exceed username rate limit → 429 AUTH_020")
    fun shouldReturn429WhenUsernameRateLimited() {
        // Given
        doThrow(RateLimitExceededException(retryAfterSeconds = 1800, dimension = "username"))
            .whenever(loginRateLimitService).checkMultiDimensional(any(), any(), anyOrNull())
        whenever(messageSource.getMessage(any<String>(), any(), any<String>(), any<Locale>()))
            .thenReturn("Too many login attempts for this username")

        val request = MockHttpServletRequest("POST", "/api/auth/login")
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Login-Username", "testuser")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // When
        filter.doFilter(request, response, chain)

        // Then
        assertEquals(429, response.status)
        assertTrue(response.contentAsString.contains("AUTH_020"))
    }

    @Test
    @DisplayName("TC4: Non-login endpoints → filter skipped")
    fun shouldSkipNonLoginEndpoints() {
        // Given — should not even call the rate limit service
        val request = MockHttpServletRequest("POST", "/api/auth/register")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // When
        filter.doFilter(request, response, chain)

        // Then
        assertNotNull(chain.request)
        verifyNoInteractions(loginRateLimitService)
    }

    @Test
    @DisplayName("TC5: GET /api/auth/login → filter skipped (only POST)")
    fun shouldSkipGetRequests() {
        val request = MockHttpServletRequest("GET", "/api/auth/login")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
        verifyNoInteractions(loginRateLimitService)
    }

    @Test
    @DisplayName("TC-extra: X-Forwarded-For header used for IP extraction")
    fun shouldUseXForwardedForForIpExtraction() {
        // Given
        doNothing().whenever(loginRateLimitService).checkMultiDimensional(any(), any(), anyOrNull())

        val request = MockHttpServletRequest("POST", "/api/auth/login")
        request.remoteAddr = "127.0.0.1" // Proxy
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // When
        filter.doFilter(request, response, chain)

        // Then — first IP from X-Forwarded-For should be used
        verify(loginRateLimitService).checkMultiDimensional(
            eq("203.0.113.50"), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC-extra: Accept-Language vi → Vietnamese error message")
    fun shouldReturnLocalizedErrorMessage() {
        // Given
        doThrow(RateLimitExceededException(retryAfterSeconds = 60, dimension = "ip"))
            .whenever(loginRateLimitService).checkMultiDimensional(any(), any(), anyOrNull())
        whenever(messageSource.getMessage(any<String>(), any(), any<String>(), eq(Locale.forLanguageTag("vi"))))
            .thenReturn("Quá nhiều lần đăng nhập thất bại")

        val request = MockHttpServletRequest("POST", "/api/auth/login")
        request.addHeader("Accept-Language", "vi")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // When
        filter.doFilter(request, response, chain)

        // Then
        assertEquals(429, response.status)
        assertEquals("vi", response.getHeader("Content-Language"))
    }
}
