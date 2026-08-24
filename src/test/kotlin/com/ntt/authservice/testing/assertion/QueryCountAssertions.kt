package com.ntt.authservice.testing.assertion

import net.ttddyy.dsproxy.QueryCountHolder
import org.junit.jupiter.api.Assertions.fail

/**
 * Kotlin DSL for asserting SQL query counts in integration tests.
 *
 * Provides a block-based API that automatically clears query counters
 * before and after the assertion block, ensuring accurate per-scenario counting.
 *
 * Usage:
 * ```kotlin
 * @SpringBootTest
 * @Import(DataSourceProxyConfig::class)
 * class QueryTest {
 *     @Test
 *     fun `login should execute 2 SELECTs`() {
 *         QueryCountAssertions.assertQueryCount(select = 2) {
 *             authService.login(credentials)
 *         }
 *     }
 *
 *     @Test
 *     fun `register should insert user and audit`() {
 *         QueryCountAssertions.assertQueryCount(select = 1, insert = 2) {
 *             authService.register(request)
 *         }
 *     }
 * }
 * ```
 *
 * Thread-safe via [QueryCountHolder]'s per-datasource-name accumulation.
 *
 * @see DataSourceProxyConfig
 * @see AssertQueryCount
 */
object QueryCountAssertions {

    /**
     * Execute [block] and assert that the SQL query counts match expected values.
     *
     * Each parameter is optional — pass `null` (default) to skip assertion for that query type.
     * Pass a non-null value to assert exact match.
     *
     * @param select expected number of SELECT queries, or null to skip
     * @param insert expected number of INSERT queries, or null to skip
     * @param update expected number of UPDATE queries, or null to skip
     * @param delete expected number of DELETE queries, or null to skip
     * @param block the code to execute while counting queries
     */
    inline fun assertQueryCount(
        select: Int? = null,
        insert: Int? = null,
        update: Int? = null,
        delete: Int? = null,
        block: () -> Unit
    ) {
        QueryCountHolder.clear()
        try {
            block()
        } finally {
            val grandTotal = QueryCountHolder.getGrandTotal()

            val mismatches = mutableListOf<String>()

            select?.let { expected ->
                val actual = grandTotal.select
                if (actual != expected.toLong()) {
                    mismatches.add("SELECT: expected $expected, got $actual")
                }
            }

            insert?.let { expected ->
                val actual = grandTotal.insert
                if (actual != expected.toLong()) {
                    mismatches.add("INSERT: expected $expected, got $actual")
                }
            }

            update?.let { expected ->
                val actual = grandTotal.update
                if (actual != expected.toLong()) {
                    mismatches.add("UPDATE: expected $expected, got $actual")
                }
            }

            delete?.let { expected ->
                val actual = grandTotal.delete
                if (actual != expected.toLong()) {
                    mismatches.add("DELETE: expected $expected, got $actual")
                }
            }

            QueryCountHolder.clear()

            if (mismatches.isNotEmpty()) {
                fail<Unit>(
                    "Query count mismatch:\n" +
                        mismatches.joinToString("\n  - ", prefix = "  - ") +
                        "\nTotal queries: S=${grandTotal.select} I=${grandTotal.insert} " +
                        "U=${grandTotal.update} D=${grandTotal.delete}"
                )
            }
        }
    }
}
