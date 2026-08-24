package com.ntt.authservice.testing.assertion

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Test-only configuration that wraps the application's [DataSource] with
 * datasource-proxy for SQL query counting.
 *
 * Uses explicit @Bean @Primary approach (vs BeanPostProcessor in base-testing-starter)
 * for predictable, single-DataSource wrapping without HikariCP conflicts.
 *
 * Import this configuration in integration tests that need query count assertions:
 *
 * ```kotlin
 * @SpringBootTest
 * @Import(DataSourceProxyConfig::class)
 * class YourTest {
 *     @Test
 *     fun `should execute expected queries`() {
 *         QueryCountAssertions.assertQueryCount(select = 2) {
 *             service.doSomething()
 *         }
 *     }
 * }
 * ```
 *
 * ⚠️ @TestConfiguration — NEVER activated in production.
 *
 * @see QueryCountAssertions
 * @see AssertQueryCountExtension
 */
@TestConfiguration
class DataSourceProxyConfig {

    @Bean
    @Primary
    fun dataSourceProxy(originalDataSource: DataSource): DataSource {
        return ProxyDataSourceBuilder.create(originalDataSource)
            .name("queryCountProxy")
            .countQuery()
            .build()
    }
}
