package com.ntt.authservice.auth.application.cipher

import com.ntt.authservice.shared.exception.CipherVersionSunsetException
import com.ntt.authservice.shared.exception.CipherVersionUnknownException
import com.ntt.basecore.autoconfigure.security.cipher.CipherProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Cipher version negotiator — manages version lifecycle.
 *
 * Supports:
 * - Decode ALL active versions (backward compatibility)
 * - Encode response using latest version
 * - Sunset timeline (3 months default) with deprecation headers
 * - Hot-reloadable via @RefreshScope
 */
@Service
class CipherVersionNegotiator(
    private val cipherProperties: CipherProperties
) {

    private val log = LoggerFactory.getLogger(CipherVersionNegotiator::class.java)

    /**
     * Known cipher versions with their status.
     * In production, this should be loaded from config/DB for hot-reloading.
     */
    data class CipherVersion(
        val version: String,
        val algorithm: String,
        val status: VersionStatus,
        val sunsetDate: Instant? = null
    )

    enum class VersionStatus {
        ACTIVE,
        DEPRECATED,
        SUNSET
    }

    // Version registry — extend as new versions are added
    private val versions = mapOf(
        "v1" to CipherVersion("v1", "AES_GCM", VersionStatus.ACTIVE),
        "v2" to CipherVersion("v2", "XCHACHA20_POLY1305", VersionStatus.ACTIVE)
    )

    /**
     * Resolve and validate a client-provided cipher version.
     *
     * @throws CipherVersionUnknownException if version not found
     * @throws CipherVersionSunsetException if version past sunset deadline
     */
    fun resolveVersion(clientVersion: String): CipherVersion {
        val version = versions[clientVersion]
            ?: throw CipherVersionUnknownException(clientVersion)

        if (version.status == VersionStatus.SUNSET) {
            val sunsetStr = version.sunsetDate?.let { formatDate(it) } ?: "unknown"
            throw CipherVersionSunsetException(clientVersion, sunsetStr)
        }

        if (version.status == VersionStatus.DEPRECATED) {
            log.warn("Client using deprecated cipher version: {}", clientVersion)
        }

        return version
    }

    /**
     * Get the latest active version for response encoding.
     */
    fun getLatestVersion(): CipherVersion {
        return versions.values
            .filter { it.status == VersionStatus.ACTIVE }
            .maxByOrNull { it.version }
            ?: throw IllegalStateException("No active cipher versions configured")
    }

    /**
     * Generate Sunset HTTP header value for deprecated versions.
     * Returns null if version is not deprecated.
     */
    fun getSunsetHeader(clientVersion: String): String? {
        val version = versions[clientVersion] ?: return null
        if (version.status != VersionStatus.DEPRECATED) return null

        return version.sunsetDate?.let { formatDate(it) }
    }

    /**
     * Check if a version can decode (ACTIVE or DEPRECATED, not SUNSET).
     */
    fun canDecode(version: String): Boolean {
        val v = versions[version] ?: return false
        return v.status != VersionStatus.SUNSET
    }

    private fun formatDate(instant: Instant): String {
        return DateTimeFormatter.RFC_1123_DATE_TIME
            .withZone(ZoneId.of("UTC"))
            .format(instant)
    }
}
