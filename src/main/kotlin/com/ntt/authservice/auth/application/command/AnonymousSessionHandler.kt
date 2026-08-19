package com.ntt.authservice.auth.application.command

import com.ntt.authservice.auth.application.AnonymousRateLimitService
import com.ntt.authservice.auth.application.AnonymousSessionResult
import com.ntt.authservice.auth.application.JwtService
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Handler for anonymous session creation.
 * Follows LoginHandler pattern — CQRS CommandHandler.
 *
 * Flow: checkRateLimit → generate sessionId → generate JWT → init Redis session → return result.
 */
@Component
class AnonymousSessionHandler(
    private val anonymousRateLimitService: AnonymousRateLimitService,
    private val jwtService: JwtService,
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry
) : CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult> {

    private val log = LoggerFactory.getLogger(AnonymousSessionHandler::class.java)

    companion object {
        private const val SESSION_PREFIX = "anon:session:"
    }

    override fun commandType(): Class<CreateAnonymousSessionCommand> = CreateAnonymousSessionCommand::class.java

    override fun handle(command: CreateAnonymousSessionCommand): AnonymousSessionResult {
        // Step 1: Rate limit check
        anonymousRateLimitService.checkRateLimit(command.ipAddress)

        // Step 2: Generate session ID
        val sessionId = UUID.randomUUID().toString()

        // Step 3: Generate anonymous JWT token (timed)
        val tokenSample = Timer.start(meterRegistry)
        val token = jwtService.generateAnonymousToken(sessionId)
        tokenSample.stop(meterRegistry.timer("auth.anonymous.token.generation.duration"))

        // Step 4: Initialize Redis session metadata
        val sessionKey = "$SESSION_PREFIX$sessionId"
        val sessionTtl = Duration.ofSeconds(securityProperties.anonymous.sessionTtlSeconds)

        val sessionData = mapOf(
            "deviceFingerprint" to (command.deviceFingerprint ?: ""),
            "ipAddress" to command.ipAddress,
            "createdAt" to Instant.now().toString(),
            "renewalCount" to "0"
        )

        redisTemplate.opsForHash<String, String>().putAll(sessionKey, sessionData)
        redisTemplate.expire(sessionKey, sessionTtl)

        // Metrics: session created
        meterRegistry.counter("auth.anonymous.sessions.created").increment()

        log.info("Anonymous session created: sessionId={} ip={}", sessionId, command.ipAddress)

        return AnonymousSessionResult(
            token = token,
            sessionId = sessionId,
            expiresIn = securityProperties.anonymous.tokenTtlSeconds
        )
    }
}
