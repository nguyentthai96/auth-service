package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import com.ntt.authservice.auth.application.event.NewDeviceLoginEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Login session service — records login history, manages active sessions,
 * detects new devices, and provides session listing/revocation.
 */
@Service
class LoginSessionService(
    private val loginSessionRepository: LoginSessionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(LoginSessionService::class.java)

    /**
     * Record a new login session.
     * Detects new device and emits NewDeviceLoginEvent.
     */
    @Transactional
    fun recordLogin(
        userId: Long,
        ipAddress: String,
        userAgent: String?,
        deviceFingerprint: String?,
        refreshTokenId: Long?
    ): LoginSessionEntity {
        val uaInfo = parseUserAgent(userAgent)
        val isNewDevice = detectNewDevice(userId, deviceFingerprint)

        val session = LoginSessionEntity().apply {
            this.userId = userId
            this.refreshTokenId = refreshTokenId
            this.ipAddress = ipAddress
            this.userAgent = userAgent
            this.deviceFingerprint = deviceFingerprint
            this.deviceType = uaInfo.deviceType
            this.browserName = uaInfo.browserName
            this.osName = uaInfo.osName
            this.isActive = true
            this.isNewDevice = isNewDevice
            this.loginAt = Instant.now()
            this.lastActivityAt = Instant.now()
        }

        val saved = loginSessionRepository.save(session)

        if (isNewDevice && !deviceFingerprint.isNullOrBlank()) {
            eventPublisher.publishEvent(
                NewDeviceLoginEvent(
                    userId = userId,
                    deviceFingerprint = deviceFingerprint,
                    ipAddress = ipAddress,
                    browserName = uaInfo.browserName,
                    osName = uaInfo.osName
                )
            )
            log.info("NEW_DEVICE_LOGIN userId={} deviceFp={} ip={}", userId, deviceFingerprint, ipAddress)
        }

        log.debug("Login session recorded sessionId={} userId={}", saved.id, userId)
        return saved
    }

    /**
     * Update last activity timestamp for session.
     */
    @Transactional
    fun updateLastActivity(sessionId: Long) {
        loginSessionRepository.findById(sessionId).ifPresent { session ->
            session.lastActivityAt = Instant.now()
            loginSessionRepository.save(session)
        }
    }

    /**
     * Get all active sessions for a user.
     */
    fun getActiveSessions(userId: Long): List<LoginSessionEntity> {
        return loginSessionRepository.findByUserIdAndIsActiveTrue(userId)
    }

    /**
     * Revoke a specific session — validates ownership.
     *
     * @return true if revoked successfully, false if not found or not owned
     */
    @Transactional
    fun revokeSession(sessionId: Long, userId: Long, reason: String = "MANUAL"): Boolean {
        val session = loginSessionRepository.findById(sessionId).orElse(null) ?: return false
        if (session.userId != userId) return false

        session.isActive = false
        session.revokedAt = Instant.now()
        session.revokeReason = reason
        loginSessionRepository.save(session)

        log.info("Session revoked sessionId={} userId={} reason={}", sessionId, userId, reason)
        return true
    }

    /**
     * Revoke the oldest active session for a user.
     */
    @Transactional
    fun revokeOldestSession(userId: Long, reason: String = "POLICY_EXCEED") {
        val oldest = loginSessionRepository.findFirstByUserIdAndIsActiveTrueOrderByLoginAtAsc(userId)
        if (oldest != null) {
            oldest.isActive = false
            oldest.revokedAt = Instant.now()
            oldest.revokeReason = reason
            loginSessionRepository.save(oldest)
            log.info("Oldest session revoked sessionId={} userId={} reason={}", oldest.id, userId, reason)
        }
    }

    /**
     * Revoke all active sessions for a user.
     */
    @Transactional
    fun revokeAllSessions(userId: Long, reason: String = "POLICY_EXCEED") {
        val sessions = loginSessionRepository.findByUserIdAndIsActiveTrue(userId)
        val now = Instant.now()
        sessions.forEach { session ->
            session.isActive = false
            session.revokedAt = now
            session.revokeReason = reason
        }
        loginSessionRepository.saveAll(sessions)
        log.info("All sessions revoked userId={} count={} reason={}", userId, sessions.size, reason)
    }

    private fun detectNewDevice(userId: Long, deviceFingerprint: String?): Boolean {
        if (deviceFingerprint.isNullOrBlank()) return false
        val existing = loginSessionRepository.findByDeviceFingerprintAndUserId(deviceFingerprint, userId)
        return existing.isEmpty()
    }

    /**
     * Simple User-Agent parsing — regex-based, no external library.
     */
    private fun parseUserAgent(userAgent: String?): UserAgentInfo {
        if (userAgent.isNullOrBlank()) return UserAgentInfo()

        val browserName = when {
            userAgent.contains("Edg/", ignoreCase = true) -> "Edge"
            userAgent.contains("Chrome/", ignoreCase = true) -> "Chrome"
            userAgent.contains("Firefox/", ignoreCase = true) -> "Firefox"
            userAgent.contains("Safari/", ignoreCase = true) && !userAgent.contains("Chrome", ignoreCase = true) -> "Safari"
            userAgent.contains("Opera", ignoreCase = true) || userAgent.contains("OPR/", ignoreCase = true) -> "Opera"
            else -> "Other"
        }

        val osName = when {
            userAgent.contains("Windows", ignoreCase = true) -> "Windows"
            userAgent.contains("Mac OS X", ignoreCase = true) -> "macOS"
            userAgent.contains("Linux", ignoreCase = true) && !userAgent.contains("Android", ignoreCase = true) -> "Linux"
            userAgent.contains("Android", ignoreCase = true) -> "Android"
            userAgent.contains("iPhone", ignoreCase = true) || userAgent.contains("iPad", ignoreCase = true) -> "iOS"
            else -> "Other"
        }

        val deviceType = when {
            userAgent.contains("Mobile", ignoreCase = true) || userAgent.contains("Android", ignoreCase = true) -> "MOBILE"
            userAgent.contains("iPad", ignoreCase = true) || userAgent.contains("Tablet", ignoreCase = true) -> "TABLET"
            else -> "DESKTOP"
        }

        return UserAgentInfo(deviceType = deviceType, browserName = browserName, osName = osName)
    }

    private data class UserAgentInfo(
        val deviceType: String? = null,
        val browserName: String? = null,
        val osName: String? = null
    )
}
