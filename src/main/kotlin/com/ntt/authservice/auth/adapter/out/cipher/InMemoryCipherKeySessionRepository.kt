package com.ntt.authservice.auth.adapter.out.cipher

import com.ntt.basecore.autoconfigure.security.cipher.key.CipherKeySession
import com.ntt.basecore.autoconfigure.security.cipher.key.CipherKeySessionRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory repository for cipher key sessions (default implementation / testing / fallback).
 */
@Component
@ConditionalOnMissingBean(name = ["customCipherKeySessionRepository"])
class InMemoryCipherKeySessionRepository : CipherKeySessionRepository {

    private val sessions = ConcurrentHashMap<String, CipherKeySession>()

    override fun save(session: CipherKeySession): CipherKeySession {
        sessions[session.keyId] = session
        return session
    }

    override fun findByKeyId(keyId: String): CipherKeySession? {
        val session = sessions[keyId] ?: return null
        if (session.isExpired) {
            sessions.remove(keyId)
            return null
        }
        return session
    }

    override fun findActiveByUserIdAndDeviceId(userId: String, deviceId: String): CipherKeySession? {
        return sessions.values.find {
            it.userId == userId && it.deviceId == deviceId && !it.isExpired && it.isActive
        }
    }

    override fun countActiveByUserId(userId: String): Int {
        return sessions.values.count {
            it.userId == userId && !it.isExpired && it.isActive
        }
    }

    override fun deactivateByKeyId(keyId: String) {
        sessions[keyId]?.let {
            sessions[keyId] = it.copy(isActive = false)
        }
    }

    override fun deactivateByDeviceId(userId: String, deviceId: String) {
        sessions.values
            .filter { it.userId == userId && it.deviceId == deviceId }
            .forEach { sessions[it.keyId] = it.copy(isActive = false) }
    }
}
