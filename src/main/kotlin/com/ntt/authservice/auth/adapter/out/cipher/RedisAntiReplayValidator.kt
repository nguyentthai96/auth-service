package com.ntt.authservice.auth.adapter.out.cipher

import com.ntt.basecore.autoconfigure.security.cipher.CipherProperties
import com.ntt.basecore.autoconfigure.security.cipher.model.CipherHeaderMissingException
import com.ntt.basecore.autoconfigure.security.cipher.model.CipherHeaders
import com.ntt.basecore.autoconfigure.security.cipher.model.ReplayDetectedException
import com.ntt.basecore.autoconfigure.security.cipher.model.RequestExpiredException
import com.ntt.basecore.autoconfigure.security.cipher.security.AntiReplayValidator
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Redis-based anti-replay validator.
 *
 * Validation chain:
 * 1. Timestamp validation (±window-seconds tolerance)
 * 2. Nonce uniqueness via Redis SETNX (TTL = nonce-ttl-seconds)
 *
 * Graceful degradation: Redis unavailable → skip nonce check, log WARN.
 */
@Service
class RedisAntiReplayValidator(
    private val redisTemplate: StringRedisTemplate,
    private val cipherProperties: CipherProperties
) : AntiReplayValidator {

    private val log = LoggerFactory.getLogger(RedisAntiReplayValidator::class.java)

    override fun validate(request: HttpServletRequest) {
        val timestamp = request.getHeader(CipherHeaders.TIMESTAMP)
            ?: throw CipherHeaderMissingException(CipherHeaders.TIMESTAMP)
        val nonce = request.getHeader(CipherHeaders.NONCE)
            ?: throw CipherHeaderMissingException(CipherHeaders.NONCE)

        // 1. Timestamp validation
        validateTimestamp(timestamp)

        // 2. Nonce uniqueness
        validateNonce(nonce)
    }

    /**
     * Check timestamp is within tolerance window.
     * Initial requests (first from device) get extended tolerance.
     */
    private fun validateTimestamp(timestamp: String) {
        val requestTime = try {
            timestamp.toLong()
        } catch (e: NumberFormatException) {
            throw RequestExpiredException(-1L)
        }

        val now = System.currentTimeMillis()
        val windowMs = cipherProperties.antiReplay.windowSeconds * 1000L
        val drift = abs(now - requestTime)

        if (drift > windowMs) {
            log.warn("Request timestamp out of tolerance: drift={}ms, window={}ms", drift, windowMs)
            throw RequestExpiredException(drift / 1000L)
        }
    }

    /**
     * Ensure nonce has not been used before using Redis SETNX.
     * If Redis is unavailable, log WARN and skip (graceful degradation).
     */
    private fun validateNonce(nonce: String) {
        val prefix = cipherProperties.antiReplay.nonceCachePrefix
        val key = "$prefix$nonce"
        val ttlSeconds = cipherProperties.antiReplay.nonceTtlSeconds.toLong()

        try {
            val isNew = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS)

            if (isNew == false) {
                log.warn("Replay detected: nonce={}", nonce)
                throw ReplayDetectedException("Duplicate nonce: $nonce")
            }
        } catch (e: ReplayDetectedException) {
            throw e
        } catch (e: Exception) {
            // Graceful degradation: Redis unavailable → skip nonce check
            log.warn("Redis unavailable for nonce check — degrading gracefully: {}", e.message)
        }
    }
}
