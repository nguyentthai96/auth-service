package com.ntt.authservice.auth.application

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class PasswordPolicyConfigProvider(
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(PasswordPolicyConfigProvider::class.java)

    companion object {
        const val REDIS_HASH_KEY = "system:config:auth_login"
    }

    // L1 Cache: In-memory Caffeine cache (TTL 30 minutes, or invalidated via Kafka)
    private val l1Cache = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .maximumSize(1)
        .build<String, PasswordPolicyConfig>()

    /**
     * Retrieves the password policy config.
     * Hierarchy: L1 (Memory) -> L2 (Redis) -> L3 (API) -> L4 (Default)
     */
    fun getConfig(): PasswordPolicyConfig {
        // Try L1 Cache
        val cached = l1Cache.getIfPresent("POLICY")
        if (cached != null) {
            return cached
        }

        // Try L2 Cache (Redis)
        val redisConfig = getFromRedis()
        if (redisConfig != null) {
            l1Cache.put("POLICY", redisConfig)
            return redisConfig
        }

        // L3 (API) - omitted for simplicity, can be implemented with Feign/RestTemplate
        
        // L4 (Default)
        log.warn("Falling back to L4 default PasswordPolicyConfig")
        val defaultConfig = PasswordPolicyConfig()
        l1Cache.put("POLICY", defaultConfig)
        return defaultConfig
    }

    fun invalidateCache() {
        log.info("Invalidating L1 password policy cache")
        l1Cache.invalidate("POLICY")
    }

    private fun getFromRedis(): PasswordPolicyConfig? {
        try {
            val hashOps = redisTemplate.opsForHash<String, String>()
            val entries = hashOps.entries(REDIS_HASH_KEY)

            if (entries.isEmpty()) {
                return null
            }

            return PasswordPolicyConfig(
                minLength = entries["password.min_length"]?.toIntOrNull() ?: 8,
                maxLength = entries["password.max_length"]?.toIntOrNull() ?: 128,
                requireUppercase = entries["password.require_uppercase"]?.toBoolean() ?: true,
                requireLowercase = entries["password.require_lowercase"]?.toBoolean() ?: true,
                requireDigit = entries["password.require_digit"]?.toBoolean() ?: true,
                requireSpecial = entries["password.require_special"]?.toBoolean() ?: false,
                minCharacterTypes = entries["password.min_character_types"]?.toIntOrNull() ?: 3,
                historyCount = entries["password.history_count"]?.toIntOrNull() ?: 5,
                maxAgeDays = entries["password.max_age_days"]?.toIntOrNull() ?: 90,
                lockoutThreshold = entries["password.lockout_threshold"]?.toIntOrNull() ?: 5,
                lockoutDurationMinutes = entries["password.lockout_duration_minutes"]?.toIntOrNull() ?: 15
            )
        } catch (ex: Exception) {
            log.error("Failed to read password policy from Redis", ex)
            return null
        }
    }
}
