package com.ntt.authservice.testing.assertion

import net.ttddyy.dsproxy.QueryCountHolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that automatically clears query counters before each test
 * and asserts expected counts after each test based on [@AssertQueryCount] annotation.
 *
 * Flow:
 * 1. **beforeEach**: [QueryCountHolder.clear] — reset all counters
 * 2. Test method executes (queries are counted by datasource-proxy)
 * 3. **afterEach**: Read [@AssertQueryCount] annotation → compare expected vs actual
 *    → [QueryCountHolder.clear] — cleanup
 *
 * If the test method does NOT have [@AssertQueryCount], afterEach silently clears
 * counters without asserting (safe for mixed test classes).
 *
 * @see AssertQueryCount
 * @see DataSourceProxyConfig
 */
class AssertQueryCountExtension : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        QueryCountHolder.clear()
    }

    override fun afterEach(context: ExtensionContext) {
        try {
            val annotation = context.requiredTestMethod
                .getAnnotation(AssertQueryCount::class.java) ?: return

            val grandTotal = QueryCountHolder.getGrandTotal()
            val summary = "Total queries: S=${grandTotal.select} I=${grandTotal.insert} " +
                "U=${grandTotal.update} D=${grandTotal.delete}"

            if (annotation.select >= 0) {
                assertEquals(
                    annotation.select.toLong(),
                    grandTotal.select,
                    "Expected ${annotation.select} SELECT queries, got ${grandTotal.select}. $summary"
                )
            }

            if (annotation.insert >= 0) {
                assertEquals(
                    annotation.insert.toLong(),
                    grandTotal.insert,
                    "Expected ${annotation.insert} INSERT queries, got ${grandTotal.insert}. $summary"
                )
            }

            if (annotation.update >= 0) {
                assertEquals(
                    annotation.update.toLong(),
                    grandTotal.update,
                    "Expected ${annotation.update} UPDATE queries, got ${grandTotal.update}. $summary"
                )
            }

            if (annotation.delete >= 0) {
                assertEquals(
                    annotation.delete.toLong(),
                    grandTotal.delete,
                    "Expected ${annotation.delete} DELETE queries, got ${grandTotal.delete}. $summary"
                )
            }
        } finally {
            QueryCountHolder.clear()
        }
    }
}
