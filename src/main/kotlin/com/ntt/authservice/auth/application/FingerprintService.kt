package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * Device fingerprint resolution and validation service.
 *
 * Hybrid priority: client-provided header → server-computed fallback.
 * Client fingerprint (browser-side) uses Canvas, WebGL, AudioContext, fonts → 99%+ accuracy.
 * Server fingerprint uses request headers (UA, Accept-Language, IP prefix) → ~85% accuracy.
 */
@Service
class FingerprintService(
    private val securityProperties: SecurityProperties
) {

    private val log = LoggerFactory.getLogger(FingerprintService::class.java)

    companion object {
        private val HEX_64_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }

    /**
     * Resolve device fingerprint from request.
     * Priority: X-Device-Fingerprint header (if valid hex-64) → server-computed fallback.
     *
     * @return fingerprint hash (64-char hex string) or null if feature disabled
     */
    fun resolveFingerprint(request: HttpServletRequest): String? {
        val props = securityProperties.fingerprint
        if (!props.enabled) return null

        // Priority 1: Client-provided header
        val clientFingerprint = request.getHeader(props.headerName)
        if (!clientFingerprint.isNullOrBlank() && HEX_64_PATTERN.matches(clientFingerprint)) {
            return clientFingerprint.lowercase()
        }

        if (!clientFingerprint.isNullOrBlank()) {
            log.debug("Invalid client fingerprint format, falling back to server computation")
        }

        // Priority 2: Server-computed fallback
        if (props.serverComputeFallback) {
            return computeServerFingerprint(request)
        }

        return null
    }

    /**
     * Validate that the fingerprint claim in JWT matches the current request fingerprint.
     *
     * @return true if fingerprints match, false otherwise
     */
    fun validateFingerprint(claimFingerprint: String?, requestFingerprint: String?): Boolean {
        if (claimFingerprint == null || requestFingerprint == null) return true
        return claimFingerprint.equals(requestFingerprint, ignoreCase = true)
    }

    /**
     * Server-computed fingerprint using available request headers.
     * SHA-256(User-Agent + Accept-Language + IP-Prefix/24 + SEC-CH-UA)
     */
    private fun computeServerFingerprint(request: HttpServletRequest): String {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val acceptLanguage = request.getHeader("Accept-Language") ?: ""
        val ipPrefix = extractIpPrefix(request.remoteAddr)
        val secChUa = request.getHeader("Sec-CH-UA") ?: ""

        val raw = "$userAgent|$acceptLanguage|$ipPrefix|$secChUa"
        return sha256Hex(raw)
    }

    /**
     * Extract IP prefix (/24 for IPv4, /48 for IPv6) for locality grouping.
     */
    private fun extractIpPrefix(ip: String): String {
        return if (ip.contains(":")) {
            // IPv6: keep first 3 groups (/48)
            ip.split(":").take(3).joinToString(":")
        } else {
            // IPv4: keep first 3 octets (/24)
            ip.split(".").take(3).joinToString(".")
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
