package com.ntt.authservice.pbac.application

import com.ntt.authservice.shared.exception.PolicyEvaluationException

/**
 * Enum-based operator strategy for PBAC condition evaluation.
 * Replaces when-clause in PolicyEvaluator.evaluateCondition() for OCP compliance.
 *
 * FR-016: Adding a new operator = adding a new enum entry. No modification to evaluation logic.
 *
 * Each entry contains a lambda that evaluates actual vs expected values.
 * Use [fromString] factory to resolve operator from database string representation.
 */
enum class ConditionOperator(val evaluate: (actual: Any?, expected: Any?) -> Boolean) {

    EQ({ actual, expected ->
        actual?.toString() == expected?.toString()
    }),

    NEQ({ actual, expected ->
        actual?.toString() != expected?.toString()
    }),

    IN({ actual, expected ->
        val set = (expected as? List<*>)?.map { it.toString() } ?: emptyList()
        actual?.toString() in set
    }),

    NOT_IN({ actual, expected ->
        val set = (expected as? List<*>)?.map { it.toString() } ?: emptyList()
        actual?.toString() !in set
    }),

    GT({ actual, expected ->
        compareAsNumbers(actual, expected) > 0
    }),

    GTE({ actual, expected ->
        compareAsNumbers(actual, expected) >= 0
    }),

    LT({ actual, expected ->
        compareAsNumbers(actual, expected) < 0
    }),

    LTE({ actual, expected ->
        compareAsNumbers(actual, expected) <= 0
    }),

    CONTAINS({ actual, expected ->
        actual?.toString()?.contains(expected?.toString() ?: "") == true
    }),

    STARTS_WITH({ actual, expected ->
        actual?.toString()?.startsWith(expected?.toString() ?: "") == true
    });

    companion object {
        /**
         * Resolve operator from database string representation (case-insensitive).
         * @throws PolicyEvaluationException if operator string is unknown
         */
        fun fromString(operator: String): ConditionOperator =
            entries.find { it.name.equals(operator, ignoreCase = true) }
                ?: throw PolicyEvaluationException("Unknown operator: $operator")
    }
}

/**
 * Compare two values as numbers. Non-numeric values default to 0.0.
 */
private fun compareAsNumbers(a: Any?, b: Any?): Int {
    val numA = (a as? Number)?.toDouble() ?: a?.toString()?.toDoubleOrNull() ?: 0.0
    val numB = (b as? Number)?.toDouble() ?: b?.toString()?.toDoubleOrNull() ?: 0.0
    return numA.compareTo(numB)
}
