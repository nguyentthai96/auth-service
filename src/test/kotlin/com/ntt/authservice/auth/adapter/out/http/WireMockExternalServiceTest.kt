package com.ntt.authservice.auth.adapter.out.http

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.ntt.authservice.auth.application.port.out.SsoGateway
import com.ntt.authservice.shared.exception.AuthException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integration tests for external HTTP service calls (SSO + Captcha)
 * using WireMock to simulate latency, faults, and circuit breaker activation.
 *
 * FR-002: Giả lập latency HTTP services
 *
 * Test scenarios:
 * 1. SSO latency exceeds timeout → exception thrown
 * 2. SSO circuit breaker opens after repeated failures
 * 3. SSO fallback throws AuthException(SSO_TOKEN_INVALID, HTTP 503)
 * 4. Captcha circuit breaker graceful degradation → returns true
 * 5. Captcha noop mode bypasses WireMock entirely
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
class WireMockExternalServiceTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.sso.provider-base-url") { "http://localhost:\${wiremock.server.port}" }
            registry.add("app.security.captcha.verify-url") { "http://localhost:\${wiremock.server.port}" }
            registry.add("app.security.captcha.provider") { "turnstile" }
            registry.add("app.security.captcha.secret-key") { "test-secret-key" }
        }
    }

    @Autowired
    @Qualifier("httpSsoGateway")
    private lateinit var ssoGateway: SsoGateway

    @Autowired
    private lateinit var captchaGateway: HttpCaptchaGateway

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun resetCircuitBreakers() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { cb ->
            cb.reset()
        }
    }

    // ==================== SSO Tests ====================

    @Test
    fun `sso latency exceeds timeout should throw exception`() {
        // WireMock stub: /oauth2/token responds after 15s (exceeds 10s read timeout)
        stubFor(
            post(urlPathEqualTo("/oauth2/token"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(15_000)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"accessToken":"tok","tokenType":"Bearer","expiresIn":3600}""")
                )
        )

        assertFailsWith<Exception> {
            ssoGateway.exchangeAuthorizationCode("google", "auth-code-123", "http://localhost/callback")
        }
    }

    @Test
    fun `sso circuit breaker opens after repeated failures`() {
        // WireMock stub: /oauth2/token returns 500 (server error)
        stubFor(
            post(urlPathEqualTo("/oauth2/token"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"internal_error"}""")
                )
        )

        val ssoBreaker = circuitBreakerRegistry.circuitBreaker("ssoProvider")

        // Make enough calls to trip the circuit breaker
        // Config: slidingWindowSize=10, failureRateThreshold=50%, minimumNumberOfCalls=5
        repeat(6) {
            runCatching {
                ssoGateway.exchangeAuthorizationCode("google", "code-$it", "http://localhost/callback")
            }
        }

        assertEquals(
            CircuitBreaker.State.OPEN,
            ssoBreaker.state,
            "Circuit breaker should be OPEN after 6 consecutive failures (threshold: 50% of 10, min 5 calls)"
        )
    }

    @Test
    fun `sso fallback throws AuthException with SSO_TOKEN_INVALID`() {
        // WireMock stub: /oauth2/token returns 500 to trigger circuit breaker
        stubFor(
            post(urlPathEqualTo("/oauth2/token"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("""{"error":"server_error"}""")
                )
        )

        // Force circuit breaker to OPEN state
        val ssoBreaker = circuitBreakerRegistry.circuitBreaker("ssoProvider")
        ssoBreaker.transitionToOpenState()

        val exception = assertFailsWith<AuthException> {
            ssoGateway.exchangeAuthorizationCode("google", "auth-code", "http://localhost/callback")
        }

        assertEquals(
            com.ntt.authservice.shared.exception.AuthErrorCode.SSO_TOKEN_INVALID,
            exception.authError,
            "Fallback should throw AuthException with SSO_TOKEN_INVALID error code"
        )
        assertEquals(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            exception.httpStatus,
            "Fallback should return HTTP 503 SERVICE_UNAVAILABLE"
        )
    }

    // ==================== Captcha Tests ====================

    @Test
    fun `captcha circuit breaker graceful degradation returns true`() {
        // WireMock stub: captcha verification endpoint returns 500
        stubFor(
            post(urlPathEqualTo("/"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("""{"error":"server_error"}""")
                )
        )

        // Force circuit breaker to OPEN state
        val captchaBreaker = circuitBreakerRegistry.circuitBreaker("captchaProvider")
        captchaBreaker.transitionToOpenState()

        // Fallback should return true (graceful degradation — allow request when captcha is down)
        val result = captchaGateway.verify("test-token-123")

        assertTrue(result, "Captcha fallback should return true (graceful degradation)")
    }

    @Test
    fun `captcha noop provider bypasses WireMock entirely`() {
        // Create a separate noop gateway instance to test noop behavior
        // The noop path is: if provider == "noop" → return true immediately, no HTTP call
        val noopGateway = HttpCaptchaGateway(
            captchaClient = object : CaptchaClient {
                override fun verify(secret: String, response: String): CaptchaVerifyResponse {
                    throw AssertionError("Should not be called in noop mode")
                }
            },
            secretKey = "",
            provider = "noop"
        )

        val result = noopGateway.verify("any-token")
        assertTrue(result, "Noop provider should return true without making HTTP call")
    }
}
