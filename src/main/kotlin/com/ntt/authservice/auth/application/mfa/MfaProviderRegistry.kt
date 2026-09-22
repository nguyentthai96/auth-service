package com.ntt.authservice.auth.application.mfa

import com.ntt.authservice.shared.exception.MfaCodeInvalidException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Registry for MFA providers — auto-discovers and indexes by method.
 * FR-012: O(1) lookup by MFA method, Spring auto-injects all MfaProvider beans.
 */
@Component
class MfaProviderRegistry(providers: List<MfaProvider>) {

    private val log = LoggerFactory.getLogger(MfaProviderRegistry::class.java)
    private val providerMap: Map<String, MfaProvider>

    init {
        providerMap = providers.associateBy { it.supportedMethod.uppercase() }
        log.info(
            "MFA provider registry initialized with {} providers: [{}]",
            providerMap.size,
            providerMap.keys.joinToString(", ")
        )
    }

    /**
     * Get provider for the specified MFA method.
     * @throws MfaCodeInvalidException if no provider registered for method
     */
    fun get(method: String): MfaProvider =
        providerMap[method.uppercase()]
            ?: throw MfaCodeInvalidException("Unsupported MFA method: $method")

    /**
     * Check if a provider is registered for the given method.
     */
    fun isSupported(method: String): Boolean =
        providerMap.containsKey(method.uppercase())
}
