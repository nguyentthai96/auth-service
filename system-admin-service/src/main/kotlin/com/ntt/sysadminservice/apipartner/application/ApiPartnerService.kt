package com.ntt.sysadminservice.apipartner.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * API Partner service — usage dashboard & IP whitelist (FR-012).
 * Aggregate API usage logs, IP whitelist enforcement.
 * 
 * ⚠️ Assumption: ApiPartnerEntity already exists in system-admin-service.
 * This modification adds usage aggregation and IP whitelist management.
 */
@Service
class ApiPartnerService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(ApiPartnerService::class.java)

    /**
     * Get aggregated API usage statistics for a partner.
     */
    fun getUsageStatistics(partnerId: Long): Map<String, Any> {
        // Aggregate from API usage logs
        @Suppress("UNCHECKED_CAST")
        val result = try {
            entityManager
                .createNativeQuery("""
                    SELECT COUNT(*) as total_calls,
                           COUNT(CASE WHEN status_code >= 200 AND status_code < 300 THEN 1 END) as success_count,
                           COUNT(CASE WHEN status_code >= 400 THEN 1 END) as error_count,
                           AVG(response_time_ms) as avg_response_time
                    FROM api_usage_logs 
                    WHERE partner_id = :partnerId
                    AND created_at >= NOW() - INTERVAL '30 days'
                """)
                .setParameter("partnerId", partnerId)
                .singleResult as Array<Any?>
        } catch (e: Exception) {
            log.warn("Failed to aggregate API usage for partner {}: {}", partnerId, e.message)
            return mapOf("totalCalls" to 0, "successCount" to 0, "errorCount" to 0, "avgResponseTimeMs" to 0)
        }

        return mapOf(
            "totalCalls" to (result[0] ?: 0),
            "successCount" to (result[1] ?: 0),
            "errorCount" to (result[2] ?: 0),
            "avgResponseTimeMs" to (result[3] ?: 0)
        )
    }

    /**
     * Update IP whitelist for an API partner.
     */
    @Transactional
    fun updateIpWhitelist(partnerId: Long, ipAddresses: List<String>) {
        // Validate IP format
        val ipRegex = Regex("""^(\d{1,3}\.){3}\d{1,3}(/\d{1,2})?$""")
        ipAddresses.forEach { ip ->
            if (!ipRegex.matches(ip)) {
                throw SysAdminException(SysAdminErrorCode.GENERAL_ERROR, "Invalid IP address format: $ip")
            }
        }

        val ipListJson = ipAddresses.joinToString(",", "[\"", "\"]") { "\"$it\"" }
            .replace("\"\"", "\"")

        // Use simple JSON array format
        val jsonArray = "[${ipAddresses.joinToString(",") { "\"$it\"" }}]"

        entityManager
            .createNativeQuery("UPDATE api_partners SET ip_whitelist = :ips::jsonb, updated_at = NOW() WHERE id = :id")
            .setParameter("ips", jsonArray)
            .setParameter("id", partnerId)
            .executeUpdate()

        log.info("IP whitelist updated for partner {}: {}", partnerId, ipAddresses)
    }

    /**
     * Validate that a request IP is in the partner's whitelist.
     */
    fun isIpWhitelisted(partnerId: Long, requestIp: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val result = try {
            entityManager
                .createNativeQuery("""
                    SELECT ip_whitelist::text FROM api_partners 
                    WHERE id = :id AND active = true
                """)
                .setParameter("id", partnerId)
                .singleResult as? String
        } catch (e: Exception) {
            return true // If no whitelist configured, allow all
        }

        if (result.isNullOrBlank() || result == "[]") return true
        return result.contains(requestIp)
    }
}
