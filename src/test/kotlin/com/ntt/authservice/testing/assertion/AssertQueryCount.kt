package com.ntt.authservice.testing.assertion

import org.junit.jupiter.api.extension.ExtendWith

/**
 * Annotation-driven query count assertion for JUnit 5 test methods.
 *
 * When placed on a test method, [AssertQueryCountExtension] automatically
 * clears query counters before the test and asserts expected counts after.
 *
 * Values of `-1` (default) mean "don't assert this query type".
 *
 * Usage:
 * ```kotlin
 * @SpringBootTest
 * @Import(DataSourceProxyConfig::class)
 * class QueryTest {
 *     @Test
 *     @AssertQueryCount(select = 1)
 *     fun `token refresh should execute single SELECT`() {
 *         authService.refreshToken(token)
 *     }
 *
 *     @Test
 *     @AssertQueryCount(select = 2, insert = 1)
 *     fun `login with audit should match expected counts`() {
 *         authService.login(credentials)
 *     }
 * }
 * ```
 *
 * @see AssertQueryCountExtension
 * @see DataSourceProxyConfig
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(AssertQueryCountExtension::class)
annotation class AssertQueryCount(
    val select: Int = -1,
    val insert: Int = -1,
    val update: Int = -1,
    val delete: Int = -1
)
