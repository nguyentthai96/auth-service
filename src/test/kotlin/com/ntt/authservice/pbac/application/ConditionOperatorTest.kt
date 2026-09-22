package com.ntt.authservice.pbac.application

import com.ntt.authservice.shared.exception.PolicyEvaluationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for ConditionOperator enum.
 * FR-016: verify all operator evaluations and fromString factory.
 */
class ConditionOperatorTest {

    @Nested
    inner class EqualityOperators {

        @Test
        fun `EQ returns true for equal strings`() {
            assertTrue(ConditionOperator.EQ.evaluate("hello", "hello"))
        }

        @Test
        fun `EQ returns false for different strings`() {
            assertFalse(ConditionOperator.EQ.evaluate("hello", "world"))
        }

        @Test
        fun `EQ handles null actual`() {
            assertFalse(ConditionOperator.EQ.evaluate(null, "hello"))
        }

        @Test
        fun `NEQ returns true for different strings`() {
            assertTrue(ConditionOperator.NEQ.evaluate("hello", "world"))
        }

        @Test
        fun `NEQ returns false for equal strings`() {
            assertFalse(ConditionOperator.NEQ.evaluate("hello", "hello"))
        }
    }

    @Nested
    inner class SetOperators {

        @Test
        fun `IN returns true when value in set`() {
            assertTrue(ConditionOperator.IN.evaluate("admin", listOf("admin", "user", "guest")))
        }

        @Test
        fun `IN returns false when value not in set`() {
            assertFalse(ConditionOperator.IN.evaluate("superadmin", listOf("admin", "user", "guest")))
        }

        @Test
        fun `NOT_IN returns true when value not in set`() {
            assertTrue(ConditionOperator.NOT_IN.evaluate("superadmin", listOf("admin", "user")))
        }

        @Test
        fun `NOT_IN returns false when value in set`() {
            assertFalse(ConditionOperator.NOT_IN.evaluate("admin", listOf("admin", "user")))
        }
    }

    @Nested
    inner class ComparisonOperators {

        @Test
        fun `GT returns true when actual greater`() {
            assertTrue(ConditionOperator.GT.evaluate(10, 5))
        }

        @Test
        fun `GT returns false when equal`() {
            assertFalse(ConditionOperator.GT.evaluate(5, 5))
        }

        @Test
        fun `GTE returns true when equal`() {
            assertTrue(ConditionOperator.GTE.evaluate(5, 5))
        }

        @Test
        fun `LT returns true when actual less`() {
            assertTrue(ConditionOperator.LT.evaluate(3, 10))
        }

        @Test
        fun `LTE returns true when equal`() {
            assertTrue(ConditionOperator.LTE.evaluate(5, 5))
        }

        @Test
        fun `comparison handles string numbers`() {
            assertTrue(ConditionOperator.GT.evaluate("10", "5"))
        }

        @Test
        fun `comparison handles null as zero`() {
            assertTrue(ConditionOperator.GT.evaluate(1, null))
        }
    }

    @Nested
    inner class StringOperators {

        @Test
        fun `CONTAINS returns true when substring present`() {
            assertTrue(ConditionOperator.CONTAINS.evaluate("hello world", "world"))
        }

        @Test
        fun `CONTAINS returns false when substring absent`() {
            assertFalse(ConditionOperator.CONTAINS.evaluate("hello", "world"))
        }

        @Test
        fun `STARTS_WITH returns true for matching prefix`() {
            assertTrue(ConditionOperator.STARTS_WITH.evaluate("admin_role", "admin"))
        }

        @Test
        fun `STARTS_WITH returns false for non-matching prefix`() {
            assertFalse(ConditionOperator.STARTS_WITH.evaluate("user_role", "admin"))
        }
    }

    @Nested
    inner class FromString {

        @Test
        fun `fromString resolves case-insensitively`() {
            assertEquals(ConditionOperator.EQ, ConditionOperator.fromString("eq"))
            assertEquals(ConditionOperator.EQ, ConditionOperator.fromString("EQ"))
            assertEquals(ConditionOperator.GT, ConditionOperator.fromString("gt"))
            assertEquals(ConditionOperator.NOT_IN, ConditionOperator.fromString("not_in"))
        }

        @Test
        fun `fromString throws for unknown operator`() {
            assertThrows<PolicyEvaluationException> {
                ConditionOperator.fromString("unknown_op")
            }
        }
    }
}
