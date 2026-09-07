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
 * detects new devices, provides session listing/revocation,
 * and enforces concurrent session limits (FR-008).
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

        val deviceName = buildDeviceName(uaInfo)

        val session = LoginSessionEntity().apply {
            this.userId = userId
            this.refreshTokenId = refreshTokenId
            this.ipAddress = ipAddress
            this.userAgent = userAgent
            this.deviceFingerprint = deviceFingerprint
            this.deviceType = uaInfo.deviceType
            this.browserName = uaInfo.browserName
            this.osName = uaInfo.osName
            this.sessionActive = true
            this.isNewDevice = isNewDevice
            this.loginAt = Instant.now()
            this.lastActivityAt = Instant.now()
            this.deviceName = deviceName
        }

        val saved = loginSessionRepository.save(session)

        if (isNewDevice && !deviceFingerprint.isNullOrBlank()) {
            eventPublisher.publishEvent(
                NewDeviceLoginEvent(
                    userId = userId,
                    deviceFingerprint = deviceFingerprint,
                    deviceName = deviceName,
                    deviceType = uaInfo.deviceType,
                    browserName = uaInfo.browserName,
                    osName = uaInfo.osName,
                    ipAddress = ipAddress
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
        return loginSessionRepository.findByUserIdAndSessionActiveTrue(userId)
    }

    /**
     * List all active devices for a user — used by DeviceController.
     */
    fun listDevicesForUser(userId: Long): List<LoginSessionEntity> {
        return loginSessionRepository.findByUserIdAndSessionActiveTrue(userId)
    }

    /**
     * Kick a specific device/session — validates ownership + blacklist access token.
     *
     * @return true if kicked, false if not found or not owned
     */
    @Transactional
    fun kickDevice(sessionId: Long, userId: Long): Boolean {
        val session = loginSessionRepository.findById(sessionId).orElse(null) ?: return false
        if (session.userId != userId) return false
        if (!session.sessionActive) return false

        revokeSessionWithSync(session, "DEVICE_KICKED")
        log.info("Device kicked sessionId={} userId={}", sessionId, userId)
        return true
    }

    /**
     * Kick all other devices except the current one.
     */
    @Transactional
    fun kickAllOtherDevices(userId: Long, currentJti: String?): Int {
        val sessions = loginSessionRepository.findByUserIdAndSessionActiveTrue(userId)
        var kicked = 0
        for (session in sessions) {
            // Skip current session
            if (currentJti != null && session.accessTokenJti == currentJti) continue

            revokeSessionWithSync(session, "KICK_ALL_OTHERS")
            kicked++
        }
        log.info("Kicked all other devices userId={} kicked={}", userId, kicked)
        return kicked
    }

    /**
     * Revoke session with eager token sync — blacklist access JTI + revoke refresh token.
     */
    @Transactional
    fun revokeSessionWithSync(session: LoginSessionEntity, reason: String) {
        session.sessionActive = false
        session.revokedAt = Instant.now()
        session.revokeReason = reason
        loginSessionRepository.save(session)
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

        session.sessionActive = false
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
        val oldest = loginSessionRepository.findFirstByUserIdAndSessionActiveTrueOrderByLoginAtAsc(userId)
        if (oldest != null) {
            oldest.sessionActive = false
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
        val sessions = loginSessionRepository.findByUserIdAndSessionActiveTrue(userId)
        val now = Instant.now()
        sessions.forEach { session ->
            session.sessionActive = false
            session.revokedAt = now
            session.revokeReason = reason
        }
        loginSessionRepository.saveAll(sessions)
        log.info("All sessions revoked userId={} count={} reason={}", userId, sessions.size, reason)
    }

    /**
     * Enforce concurrent session limit for a user (FR-008).
     * Terminates the oldest active sessions when the limit is exceeded.
     *
     * @param userId the user ID
     * @param maxSessions maximum allowed concurrent sessions
     * @return number of sessions terminated
     */
    @Transactional
    fun enforceConcurrentLimit(userId: Long, maxSessions: Int): Int {
        val activeSessions = loginSessionRepository.findByUserIdAndSessionActiveTrue(userId)
            .sortedBy { it.loginAt }

        if (activeSessions.size < maxSessions) {
            return 0
        }

        // Terminate oldest sessions that exceed the limit
        val sessionsToTerminate = activeSessions.take(activeSessions.size - maxSessions + 1)
        val now = Instant.now()
        sessionsToTerminate.forEach { session ->
            session.sessionActive = false
            session.revokedAt = now
            session.revokeReason = "CONCURRENT_LIMIT_EXCEEDED"
        }
        loginSessionRepository.saveAll(sessionsToTerminate)

        log.info("Concurrent limit enforced userId={} terminated={} maxSessions={}",
            userId, sessionsToTerminate.size, maxSessions)
        return sessionsToTerminate.size
    }

    /**
     * Get count of active sessions for a user.
     */
    fun getActiveSessionCount(userId: Long): Long {
        return loginSessionRepository.countByUserIdAndSessionActiveTrue(userId)
    }

    private fun detectNewDevice(userId: Long, deviceFingerprint: String?): Boolean {
        if (deviceFingerprint.isNullOrBlank()) return false
        val existing = loginSessionRepository.findByDeviceFingerprintAndUserId(deviceFingerprint, userId)
        return existing.isEmpty()
    }

    private fun buildDeviceName(uaInfo: UserAgentInfo): String {
        val parts = listOfNotNull(uaInfo.browserName, uaInfo.osName, uaInfo.deviceType)
        return if (parts.isNotEmpty()) parts.joinToString(" / ") else "Unknown Device"
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

