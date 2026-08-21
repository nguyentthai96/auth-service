package com.ntt.authservice.auth.application

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.AnonymousDataLimitExceededException
import com.ntt.authservice.shared.exception.AnonymousSessionExpiredException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Redis CRUD service for anonymous session data.
 * Manages namespaced key-value data within anonymous sessions with size enforcement.
 *
 * Redis key format: anon:data:{sessionId}:{namespace}:{key}
 * Session key format: anon:session:{sessionId}
 *
 * Data integrity: storeData() uses Lua atomic_data_store script for TOCTOU-safe
 * size check + write + counter increment (FR-007). Running dataSize counter
 * replaces O(N) SCAN+STRLEN with O(1) HGET (FR-005).
 */
@Service
class AnonymousSessionDataService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry,
    private val atomicDataStoreScript: DefaultRedisScript<Long>
) {

    private val log = LoggerFactory.getLogger(AnonymousSessionDataService::class.java)

    companion object {
        private const val SESSION_PREFIX = "anon:session:"
        private const val DATA_PREFIX = "anon:data:"
    }

    /**
     * Store data in an anonymous session namespace.
     * Uses Lua script for atomic size check + write + counter increment (TOCTOU-safe).
     * Validates session existence before write.
     */
    @Observed(
        name = "anonymous.data.store",
        contextualName = "store-anonymous-data"
    )
    fun storeData(sessionId: String, namespace: String, key: String, value: String) {
        if (!verifySessionExists(sessionId)) {
            throw AnonymousSessionExpiredException(sessionId)
        }

        val sessionKey = "$SESSION_PREFIX$sessionId"
        val dataKey = dataKey(sessionId, namespace, key)
        val maxSize = securityProperties.anonymous.maxDataSizeBytes
        val sessionTtl = redisTemplate.getExpire(sessionKey)
            ?: securityProperties.anonymous.sessionTtlSeconds

        val result = redisTemplate.execute(
            atomicDataStoreScript,
            listOf(sessionKey, dataKey),
            maxSize.toString(),
            value,
            sessionTtl.toString()
        )

        if (result == -1L) {
            meterRegistry.counter("auth.anonymous.data.size_exceeded").increment()
            val currentSize = redisTemplate.opsForHash<String, String>()
                .get(sessionKey, "dataSize")?.toLongOrNull() ?: 0L
            throw AnonymousDataLimitExceededException(
                currentSize + value.toByteArray().size.toLong(),
                maxSize
            )
        }
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
     * Decrements running dataSize counter by the size of deleted data (DD-109: Kotlin HINCRBY, not Lua).
     */
    fun deleteData(sessionId: String, namespace: String, key: String) {
        if (!verifySessionExists(sessionId)) {
            throw AnonymousSessionExpiredException(sessionId)
        }
        val dataKey = dataKey(sessionId, namespace, key)
        // Get size before deletion for counter decrement
        val deletedSize = redisTemplate.opsForValue().size(dataKey) ?: 0L
        redisTemplate.delete(dataKey)
        if (deletedSize > 0) {
            val sessionKey = "$SESSION_PREFIX$sessionId"
            redisTemplate.opsForHash<String, String>()
                .increment(sessionKey, "dataSize", -deletedSize)
        }
    }

    /**
     * Transfer all anonymous session data to an authenticated user's namespace.
     * Uses pipeline MGET + pipeline MSET for O(3) RTT instead of O(2N) (FR-003).
     *
     * @return DataTransferResult with item count and namespace list
     */
    @Observed(
        name = "anonymous.data.transfer",
        contextualName = "transfer-anonymous-data"
    )
    fun transferData(sessionId: String, userId: Long): DataTransferResult {
        val pattern = "$DATA_PREFIX$sessionId:*"
        val promotedTtl = Duration.ofSeconds(securityProperties.anonymous.promotedDataTtlSeconds)
        val scanCount = securityProperties.anonymous.scanCount
        val namespaces = mutableSetOf<String>()

        // Step 1: SCAN collect all keys
        val allKeys = mutableListOf<String>()
        try {
            redisTemplate.execute { connection ->
                val cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(scanCount.toLong()).build()
                )
                cursor.use {
                    while (cursor.hasNext()) {
                        allKeys.add(String(cursor.next()))
                    }
                }
                null
            }
        } catch (ex: Exception) {
            log.error("Error scanning keys for transfer session={}", sessionId, ex)
            return DataTransferResult(0, emptyList(), partial = true)
        }

        if (allKeys.isEmpty()) {
            return DataTransferResult(0, emptyList(), partial = false)
        }

        try {
            // Step 2: Pipeline GET all values
            val values = redisTemplate.executePipelined { connection ->
                allKeys.forEach { key ->
                    connection.stringCommands().get(key.toByteArray())
                }
                null
            }

            // Step 3: Build user key mappings + Pipeline SET all
            val keyValuePairs = mutableListOf<Triple<String, String, String>>() // userKey, value, namespace
            allKeys.forEachIndexed { index, rawKey ->
                val value = values.getOrNull(index) as? String ?: return@forEachIndexed
                val parts = rawKey.removePrefix("$DATA_PREFIX$sessionId:").split(":", limit = 2)
                if (parts.size == 2) {
                    val namespace = parts[0]
                    val dataKey = parts[1]
                    namespaces.add(namespace)
                    val userKey = "user:session_data:$userId:$namespace:$dataKey"
                    keyValuePairs.add(Triple(userKey, value, namespace))
                }
            }

            redisTemplate.executePipelined { connection ->
                keyValuePairs.forEach { (userKey, value, _) ->
                    connection.stringCommands().setEx(
                        userKey.toByteArray(),
                        promotedTtl.seconds,
                        value.toByteArray()
                    )
                }
                null
            }

            log.info("Transferred {} items from session {} to user {} (namespaces: {})",
                keyValuePairs.size, sessionId, userId, namespaces)
            return DataTransferResult(keyValuePairs.size, namespaces.toList(), partial = false)
        } catch (ex: Exception) {
            log.error("Error in pipeline transfer session={}", sessionId, ex)
            return DataTransferResult(0, namespaces.toList(), partial = true)
        }
    }

    /**
     * Get total data size for an anonymous session from running counter (O(1)).
     * Falls back to 0 if counter not set (backward compatible with pre-existing sessions).
     */
    fun getSessionDataSize(sessionId: String): Long {
        val sessionKey = "$SESSION_PREFIX$sessionId"
        return try {
            redisTemplate.opsForHash<String, String>()
                .get(sessionKey, "dataSize")?.toLongOrNull() ?: 0L
        } catch (ex: Exception) {
            log.warn("Error reading dataSize for session={}", sessionId, ex)
            0L
        }
    }

    /**
     * Calculate total data size by scanning all data keys and summing STRLEN.
     * Kept as fallback for potential reconciliation (FR-012 deferred).
     */
    @Suppress("unused")
    private fun getSessionDataSizeScan(sessionId: String): Long {
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
            log.warn("Error calculating session data size (scan) for session={}", sessionId, ex)
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
