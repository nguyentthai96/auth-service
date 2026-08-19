package com.ntt.authservice.auth.application

/**
 * Result of an anonymous session promotion attempt.
 * Used by SessionPromotionService and returned to LoginHandler/RegisterHandler.
 */
data class PromotionResult(
    val status: Status,
    val itemCount: Int = 0,
    val namespaces: List<String> = emptyList()
) {
    enum class Status {
        SUCCESS,   // Full promotion completed
        PARTIAL,   // Token blacklisted but data transfer failed
        FAILED,    // Session not found or promotion could not start
        CONFLICT,  // Another promotion in progress (distributed lock)
        SKIPPED    // No anonymous session ID provided
    }
}
