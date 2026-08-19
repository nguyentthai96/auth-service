package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousDataLimitExceededException
import com.ntt.authservice.shared.exception.AnonymousSessionExpiredException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Redis CRUD service for anonymous session data.
 * Manages namespaced key-value data within anonymous sessions with size enforcement.
 *
 * Redis key format: anon:data:{sessionId}:{namespace}:{key}
 * Session key format: anon:session:{sessionId}
 */
@Service
class AnonymousSessionDataService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(AnonymousSessionDataService::class.java)

    companion object {
        private const val SESSION_PREFIX = "anon:session:"
        private const val DATA_PREFIX = "anon:data:"
    }

    /**
     * Store data in an anonymous session namespace.
     * Validates session existence and enforces data size limit.
     */
    fun storeData(sessionId: String, namespace: String, key: String, value: String) {
        if (!verifySessionExists(sessionId)) {
            throw AnonymousSessionExpiredException(sessionId)
        }

        // Check data size limit
        val currentSize = getSessionDataSize(sessionId)
        val newDataSize = value.toByteArray().size.toLong()
        val maxSize = securityProperties.anonymous.maxDataSizeBytes
        if (currentSize + newDataSize > maxSize) {
            meterRegistry.counter("auth.anonymous.data.size_exceeded").increment()
            throw AnonymousDataLimitExceededException(currentSize + newDataSize, maxSize)
        }

        val dataKey = dataKey(sessionId, namespace, key)
        val sessionTtl = redisTemplate.getExpire("$SESSION_PREFIX$sessionId")
            ?: securityProperties.anonymous.sessionTtlSeconds

        redisTemplate.opsForValue().set(dataKey, value, Duration.ofSeconds(sessionTtl))

        // Metrics: data stored
        meterRegistry.counter("auth.anonymous.data.stored").increment()
    }

    /**
     * Retrieve data from an anonymous session namespace.
     */
    fun getData(sessionId: String, namespace: String, key: String): String? {
        if (!verifySessionExists(sessionId)) {
            throw AnonymousSessionExpiredException(sessionId)
        }
        return redisTemplate.opsForValue().get(dataKey(sessionId, namespace, key))
    }

    /**
     * Delete data from an anonymous session namespace.
     */
    fun deleteData(sessionId: String, namespace: String, key: String) {
        if (!verifySessionExists(sessionId)) {
            throw AnonymousSessionExpiredException(sessionId)
        }
        redisTemplate.delete(dataKey(sessionId, namespace, key))
    }

    /**
     * Transfer all anonymous session data to an authenticated user's namespace.
     * Uses SCAN (not KEYS) to avoid blocking Redis.
     *
     * @return DataTransferResult with item count and namespace list
     */
    fun transferData(sessionId: String, userId: Long): DataTransferResult {
        val pattern = "$DATA_PREFIX$sessionId:*"
        val promotedTtl = Duration.ofSeconds(securityProperties.anonymous.promotedDataTtlSeconds)
        var itemCount = 0
        val namespaces = mutableSetOf<String>()

        try {
            redisTemplate.execute { connection ->
                val cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build()
                )
                cursor.use {
                    while (cursor.hasNext()) {
                        val rawKey = String(cursor.next())
                        val value = redisTemplate.opsForValue().get(rawKey) ?: continue

                        // Extract namespace from key: anon:data:{sessionId}:{namespace}:{key}
                        val parts = rawKey.removePrefix("$DATA_PREFIX$sessionId:").split(":", limit = 2)
                        if (parts.size == 2) {
                            val namespace = parts[0]
                            val dataKey = parts[1]
                            namespaces.add(namespace)

                            // Copy to user namespace
                            val userKey = "user:session_data:$userId:$namespace:$dataKey"
                            redisTemplate.opsForValue().set(userKey, value, promotedTtl)
                            itemCount++
                        }
                    }
                }
                null
            }
        } catch (ex: Exception) {
            log.error("Error transferring anonymous session data for session={}, transferred={} items", sessionId, itemCount, ex)
            return DataTransferResult(itemCount, namespaces.toList(), partial = true)
        }

        log.info("Transferred {} items from anonymous session {} to user {} (namespaces: {})",
            itemCount, sessionId, userId, namespaces)

        return DataTransferResult(itemCount, namespaces.toList(), partial = false)
    }

    /**
     * Calculate total data size for an anonymous session by summing STRLEN of all data keys.
     */
    fun getSessionDataSize(sessionId: String): Long {
        val pattern = "$DATA_PREFIX$sessionId:*"
        var totalSize = 0L

        try {
            redisTemplate.execute { connection ->
                val cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build()
                )
                cursor.use {
                    while (cursor.hasNext()) {
                        val rawKey = String(cursor.next())
                        val length = redisTemplate.opsForValue().size(rawKey) ?: 0
                        totalSize += length
                    }
                }
                null
            }
        } catch (ex: Exception) {
            log.warn("Error calculating session data size for session={}", sessionId, ex)
        }

        return totalSize
    }

    /**
     * Check if an anonymous session exists in Redis.
     */
    fun verifySessionExists(sessionId: String): Boolean {
        return redisTemplate.hasKey("$SESSION_PREFIX$sessionId") == true
    }

    /**
     * Delete all data keys for a given anonymous session.
     */
    fun deleteAllSessionData(sessionId: String) {
        val pattern = "$DATA_PREFIX$sessionId:*"
        try {
            redisTemplate.execute { connection ->
                val cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build()
                )
                val keysToDelete = mutableListOf<String>()
                cursor.use {
                    while (cursor.hasNext()) {
                        keysToDelete.add(String(cursor.next()))
                    }
                }
                if (keysToDelete.isNotEmpty()) {
                    redisTemplate.delete(keysToDelete)
                }
                null
            }
        } catch (ex: Exception) {
            log.warn("Error deleting session data for session={}", sessionId, ex)
        }
    }

    private fun dataKey(sessionId: String, namespace: String, key: String): String =
        "$DATA_PREFIX$sessionId:$namespace:$key"

    /**
     * Result of anonymous session data transfer during promotion.
     */
    data class DataTransferResult(
        val itemCount: Int,
        val namespaces: List<String>,
        val partial: Boolean = false
    )
}
