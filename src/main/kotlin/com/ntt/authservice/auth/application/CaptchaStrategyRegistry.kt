package com.ntt.authservice.auth.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Strategy interface for CAPTCHA verification (FR-014).
 * Supports multiple CAPTCHA types (image, altcha/PoW).
 * Client selects type via `X-Captcha-Type` header.
 */
interface CaptchaStrategy {
    /** Unique type identifier (e.g., "image", "altcha"). */
    val type: String

    /** Verify a CAPTCHA token/response. */
    fun verify(token: String): Boolean
}

/**
 * Registry for CAPTCHA strategies — selects strategy by type.
 * Delegates to configured default when no type specified.
 */
@Component
class CaptchaStrategyRegistry(
    strategies: List<CaptchaStrategy>,
    private val securityProperties: com.ntt.authservice.shared.config.SecurityProperties
) {
    private val log = LoggerFactory.getLogger(CaptchaStrategyRegistry::class.java)
    private val strategyMap: Map<String, CaptchaStrategy> = strategies.associateBy { it.type }

    /**
     * Get the CAPTCHA strategy for the given type.
     * Falls back to configured default type if not specified.
     */
    fun getStrategy(type: String? = null): CaptchaStrategy {
        val resolvedType = type ?: securityProperties.imageCaptcha.defaultType
        return strategyMap[resolvedType]
            ?: strategyMap.values.firstOrNull()
            ?: throw IllegalStateException("No CAPTCHA strategy registered")
    }

    /**
     * Verify CAPTCHA using the appropriate strategy.
     */
    fun verify(token: String, type: String? = null): Boolean {
        val strategy = getStrategy(type)
        return strategy.verify(token)
    }

    /**
     * Check if a specific CAPTCHA type is available.
     */
    fun isTypeAvailable(type: String): Boolean = strategyMap.containsKey(type)
}
