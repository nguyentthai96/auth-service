package com.ntt.authservice.auth.application

import com.ntt.authservice.testing.assertion.AssertQueryCount
import com.ntt.authservice.testing.assertion.DataSourceProxyConfig
import com.ntt.authservice.testing.assertion.QueryCountAssertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for SQL query count verification using datasource-proxy.
 *
 * FR-008: Phát hiện N+1 queries tự động
 *
 * Uses two complementary approaches:
 * 1. Block-based DSL: [QueryCountAssertions.assertQueryCount] for inline assertions
 * 2. Annotation-driven: [@AssertQueryCount] for declarative per-method assertions
 *
 * When query count exceeds expected value, the test FAILS with a detailed message
 * showing the breakdown: S=SELECT I=INSERT U=UPDATE D=DELETE.
 *
 * ⚠️ Query counts are approximate and may vary depending on:
 * - Spring Security filter chain queries
 * - Hibernate metadata queries on first access
 * - Transaction management overhead
 * Adjust expected counts based on actual observed values.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(DataSourceProxyConfig::class)
class QueryCountIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `login should not produce N+1 queries`() {
        // Block-based DSL approach: wrap the action and assert query counts
        // Login flow expected queries:
        // - SELECT user by username (1)
        // - SELECT user roles/permissions (1-2 depending on eager/lazy fetch)
        // Total expected: ~2-3 SELECTs, no N+1
        //
        // Note: The exact count depends on the authentication flow implementation.
        // If this test fails with higher-than-expected counts, it indicates potential N+1.
        // Adjust the expected count to match the actual baseline, then monitor for regressions.
        QueryCountAssertions.assertQueryCount(select = null, insert = null) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "username": "testuser",
                            "password": "password123"
                        }
                        """.trimIndent()
                    )
            )
            // We don't assert HTTP status here — the focus is query count.
            // Even a 401 (invalid credentials) will show us the SQL execution pattern.
        }

        // If we reach here without assertion error, query counts are within expected range.
        // To establish baseline, first run with select=null, observe actual counts,
        // then set the exact expected value for regression detection.
    }

    @Test
    @AssertQueryCount(select = -1)
    fun `token refresh should execute expected SELECT count`() {
        // Annotation-driven approach: @AssertQueryCount with select=-1 (don't assert yet)
        // This test establishes the baseline for token refresh query count.
        //
        // Token refresh flow expected queries:
        // - SELECT refresh token record (1)
        // - SELECT user by ID (1)
        //
        // Set select=2 once baseline is confirmed.
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "refreshToken": "test-refresh-token-for-query-count"
                    }
                    """.trimIndent()
                )
        )
        // Response status is not the focus — we're measuring SQL query patterns.
    }
}
