package com.ntt.authservice.shared.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit

/**
 * Abstract two-tier cache: L1 (Caffeine in-memory) → L2 (Redis distributed).
 *
 * Extracted from MultiTierPermissionCache L1+L2 orchestration pattern (Layer R — cache abstraction).
 *
 * Read path:  L1 → L2 → null (caller loads from source + puts back).
 * Write path: L1 + L2 simultaneously.
 * Invalidate: Evict from both L1 + L2.
 *
 * Subclasses provide:
 * - [loadFromSource]: callback when both L1 and L2 miss (optional, returns null by default)
 * - [serializeForRedis]: convert value to String for Redis storage
 * - [deserializeFromRedis]: convert Redis String back to value
 *
 * @param K cache key type
 * @param V cache value type
 */
abstract class AbstractTwoTierCache<K : Any, V : Any>(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    /** Redis key prefix, e.g., "auth:perm:" */
    private val keyPrefix: String,
    /** L1 max entries (Caffeine). Default 10_000. */
    l1MaxSize: Long = 10_000,
    /** L1 TTL in seconds (Caffeine). Default 30. */
    private val l1TtlSeconds: Long = 30,
    /** L2 TTL in seconds (Redis). Default 1800 (30 min). */
    private val l2TtlSeconds: Long = 1800
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /** L1 cache (Caffeine). Key = string representation, Value = cached object. */
    protected val l1Cache: Cache<String, V> = Caffeine.newBuilder()
        .maximumSize(l1MaxSize)
        .expireAfterWrite(l1TtlSeconds, TimeUnit.SECONDS)
        .recordStats()
        .build()

    // --- Abstract template methods ---

    /**
     * Convert cache key to a unique Redis key suffix.
     * Combined with [keyPrefix] to form full Redis key.
     */
    protected abstract fun toKeyString(key: K): String

    /**
     * Serialize value to JSON string for Redis L2 storage.
     * Default: Jackson ObjectMapper serialization.
     */
    protected open fun serializeForRedis(value: V): String {
        return objectMapper.writeValueAsString(value)
    }

    /**
     * Deserialize JSON string from Redis back to value object.
     */
    protected abstract fun deserializeFromRedis(json: String): V?

    /**
     * Load from source when both L1 and L2 miss.
     * Override this to implement automatic cache-aside pattern.
     * Default returns null — caller must handle miss explicitly.
     */
    protected open fun loadFromSource(key: K): V? = null

    // --- Public API ---

    /**
     * Get value from cache. L1 → L2 → [loadFromSource].
     */
    fun get(key: K): V? {
        val keyStr = toKeyString(key)

        // L1 check
        l1Cache.getIfPresent(keyStr)?.let {
            return it
        }

        // L2 check
        try {
            val redisKey = "$keyPrefix$keyStr"
            val json = redisTemplate.opsForValue().get(redisKey)
            if (json != null) {
                val value = deserializeFromRedis(json)
                if (value != null) {
                    l1Cache.put(keyStr, value) // backfill L1
                    return value
                }
            }
        } catch (ex: Exception) {
            log.warn("Redis L2 get failed for key [{}], falling back: {}", keyStr, ex.message)
        }

        // Source load (optional)
        val loaded = loadFromSource(key)
        if (loaded != null) {
            put(key, loaded)
        }
        return loaded
    }

    /**
     * Put value into both L1 and L2.
     */
    fun put(key: K, value: V) {
        val keyStr = toKeyString(key)

        // L1
        l1Cache.put(keyStr, value)

        // L2
        try {
            val redisKey = "$keyPrefix$keyStr"
            redisTemplate.opsForValue().set(
                redisKey,
                serializeForRedis(value),
                l2TtlSeconds,
                TimeUnit.SECONDS
            )
        } catch (ex: Exception) {
            log.warn("Redis L2 put failed for key [{}]: {}", keyStr, ex.message)
        }
    }

    /**
     * Invalidate a specific key from both L1 and L2.
     */
    fun invalidate(key: K) {
        val keyStr = toKeyString(key)
        l1Cache.invalidate(keyStr)
        try {
            redisTemplate.delete("$keyPrefix$keyStr")
        } catch (ex: Exception) {
            log.warn("Redis L2 invalidate failed for key [{}]: {}", keyStr, ex.message)
        }
    }

    /**
     * Invalidate all entries from both L1 and L2 (matching prefix).
     */
    fun invalidateAll() {
        l1Cache.invalidateAll()
        try {
            val keys = redisTemplate.keys("$keyPrefix*")
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
            }
        } catch (ex: Exception) {
            log.warn("Redis L2 invalidateAll failed: {}", ex.message)
        }
    }

    /**
     * Return L1 cache stats for monitoring.
     */
    fun stats(): CacheStats {
        val caffeineStats = l1Cache.stats()
        return CacheStats(
            l1HitCount = caffeineStats.hitCount(),
            l1MissCount = caffeineStats.missCount(),
            l1EvictionCount = caffeineStats.evictionCount(),
            l1Size = l1Cache.estimatedSize()
        )
    }

    data class CacheStats(
        val l1HitCount: Long,
        val l1MissCount: Long,
        val l1EvictionCount: Long,
        val l1Size: Long
    )
}
