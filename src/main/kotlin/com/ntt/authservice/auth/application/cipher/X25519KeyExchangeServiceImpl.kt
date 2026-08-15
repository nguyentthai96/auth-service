package com.ntt.authservice.auth.application.cipher

import com.ntt.authservice.auth.adapter.out.persistence.entity.CipherKeySessionEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.CipherKeySessionJpaRepository
import com.ntt.authservice.shared.exception.CipherMaxDevicesException
import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithm
import com.ntt.basecore.autoconfigure.security.cipher.key.CipherKeySession
import com.ntt.basecore.autoconfigure.security.cipher.key.CipherKeySessionRepository
import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeRequest
import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeResponse
import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeService
import com.ntt.basecore.autoconfigure.security.cipher.model.CipherProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import java.util.Base64
import java.util.UUID
import javax.crypto.KeyAgreement

/**
 * X25519 ECDH key exchange implementation.
 *
 * Flow:
 * 1. Receive client public key (Base64-encoded X25519)
 * 2. Generate ephemeral server key pair
 * 3. ECDH shared secret computation
 * 4. HKDF-SHA256 key derivation → c2s + s2c symmetric keys
 * 5. Persist to Redis + JPA
 * 6. Zero-fill sensitive material
 *
 * Idempotent: same clientPublicKey + deviceId → return existing session.
 */
@Service
class X25519KeyExchangeServiceImpl(
    private val keySessionRepository: CipherKeySessionRepository,
    private val jpaRepository: CipherKeySessionJpaRepository,
    private val cipherProperties: CipherProperties
) : KeyExchangeService {

    private val log = LoggerFactory.getLogger(X25519KeyExchangeServiceImpl::class.java)
    private val algorithm = CipherAlgorithm.AES_GCM

    override fun exchange(request: KeyExchangeRequest): KeyExchangeResponse {
        // Idempotency: check existing active session for user + device
        val userId = request.userId ?: "anonymous"
        val existing = keySessionRepository.findActiveByUserIdAndDeviceId(userId, request.deviceId)
        if (existing != null && !existing.isExpired) {
            log.debug("Returning existing key session: keyId={}, deviceId={}", existing.keyId, request.deviceId)
            return KeyExchangeResponse(
                serverPublicKey = "", // Client already has it from initial exchange
                keyId = existing.keyId,
                keyVersion = existing.keyVersion,
                algorithm = existing.algorithmId,
                expiresAt = existing.expiresAt
            )
        }

        // Max devices check
        val maxDevices = cipherProperties.keyExchange.maxDevicesPerUser
        val activeCount = keySessionRepository.countActiveByUserId(userId)
        if (activeCount >= maxDevices) {
            throw CipherMaxDevicesException(maxDevices)
        }

        // Deactivate old sessions for this device
        keySessionRepository.deactivateByDeviceId(userId, request.deviceId)

        // Generate server key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("X25519")
        val serverKeyPair = keyPairGenerator.generateKeyPair()
        val serverPublicKeyBytes = serverKeyPair.public.encoded
        val serverPrivateKey = serverKeyPair.private

        // Decode client public key
        val clientPublicKeyBytes = Base64.getDecoder().decode(request.clientPublicKey)
        val clientPublicKeySpec = X509EncodedKeySpec(clientPublicKeyBytes)
        val keyFactory = KeyFactory.getInstance("X25519")
        val clientPublicKey = keyFactory.generatePublic(clientPublicKeySpec)

        // ECDH shared secret
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(serverPrivateKey)
        keyAgreement.doPhase(clientPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        try {
            // HKDF key derivation
            val salt = "${request.deviceId}:${userId}".toByteArray()
            val c2sKey = hkdfSha256(sharedSecret, salt, "cipher-c2s".toByteArray(), 32)
            val s2cKey = hkdfSha256(sharedSecret, salt, "cipher-s2c".toByteArray(), 32)

            val now = System.currentTimeMillis()
            val sessionTtl = cipherProperties.keyExchange.sessionTtl
            val keyId = UUID.randomUUID().toString()

            val session = CipherKeySession(
                keyId = keyId,
                userId = userId,
                deviceId = request.deviceId,
                platform = request.platform,
                algorithmId = algorithm.name,
                clientToServerKey = c2sKey,
                serverToClientKey = s2cKey,
                keyVersion = 1,
                appVersion = request.appVersion,
                createdAt = now,
                expiresAt = now + sessionTtl,
                isActive = true
            )

            // Persist to both Redis and JPA
            keySessionRepository.save(session)
            jpaRepository.save(toEntity(session))

            log.info("Key exchange complete: keyId={}, userId={}, deviceId={}, platform={}",
                keyId, userId, request.deviceId, request.platform)

            return KeyExchangeResponse(
                serverPublicKey = Base64.getEncoder().encodeToString(serverPublicKeyBytes),
                keyId = keyId,
                keyVersion = session.keyVersion,
                algorithm = algorithm.name,
                expiresAt = session.expiresAt
            )
        } finally {
            // Zero-fill sensitive material
            Arrays.fill(sharedSecret, 0.toByte())
        }
    }

    /**
     * HKDF-SHA256 key derivation.
     * Uses Tink's Hkdf utility when available, otherwise HMAC-based extract-and-expand.
     */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        return com.google.crypto.tink.subtle.Hkdf.computeHkdf(
            "HMACSHA256", ikm, salt, info, length
        )
    }

    private fun toEntity(session: CipherKeySession): CipherKeySessionEntity {
        return CipherKeySessionEntity(
            keyId = session.keyId,
            userId = session.userId,
            deviceId = session.deviceId,
            platform = session.platform,
            algorithmId = session.algorithmId,
            clientToServerKey = session.clientToServerKey,
            serverToClientKey = session.serverToClientKey,
            keyVersion = session.keyVersion,
            appVersion = session.appVersion,
            createdAt = session.createdAt,
            expiresAt = session.expiresAt,
            isActive = session.isActive
        )
    }
}
