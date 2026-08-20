package com.ntt.accountservice.device.application

import com.ntt.accountservice.device.adapter.`in`.web.dto.DeviceResponse
import com.ntt.accountservice.device.adapter.out.persistence.entity.UserDeviceEntity
import com.ntt.accountservice.shared.exception.AccountErrorCode
import com.ntt.accountservice.shared.exception.AccountException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Device management service (FR-007).
 * Tracks login devices, trust management, remote logout.
 */
@Service
class DeviceService(
    private val entityManager: EntityManager,
    @Value("\${app.device.max-per-user:10}") private val maxDevicesPerUser: Int = 10,
    @Value("\${app.device.trust-ttl-days:30}") private val trustTtlDays: Long = 30
) {

    private val log = LoggerFactory.getLogger(DeviceService::class.java)

    /**
     * List all devices for a user.
     */
    fun getDevices(userId: Long): List<DeviceResponse> {
        val devices = entityManager
            .createQuery("SELECT d FROM UserDeviceEntity d WHERE d.userId = :userId AND d.active = true ORDER BY d.lastUsedAt DESC", UserDeviceEntity::class.java)
            .setParameter("userId", userId)
            .resultList
        return devices.map { DeviceResponse.from(it) }
    }

    /**
     * Trust or untrust a device — trusted devices skip MFA for trustTtlDays.
     */
    @Transactional
    fun trustDevice(userId: Long, deviceId: Long, trust: Boolean): DeviceResponse {
        val device = findDeviceByIdAndUser(deviceId, userId)
            ?: throw AccountException(AccountErrorCode.DEVICE_NOT_FOUND, "Device not found: id=$deviceId")

        device.trusted = trust
        device.trustedUntil = if (trust) Instant.now().plus(trustTtlDays, ChronoUnit.DAYS) else null
        entityManager.merge(device)

        log.info("Device trust updated: deviceId={}, userId={}, trusted={}", deviceId, userId, trust)
        return DeviceResponse.from(device)
    }

    /**
     * Remove a device — remote logout.
     */
    @Transactional
    fun removeDevice(userId: Long, deviceId: Long) {
        val device = findDeviceByIdAndUser(deviceId, userId)
            ?: throw AccountException(AccountErrorCode.DEVICE_NOT_FOUND, "Device not found: id=$deviceId")

        device.active = false
        entityManager.merge(device)

        log.info("Device removed (remote logout): deviceId={}, userId={}", deviceId, userId)
    }

    /**
     * Register or update a device on login.
     */
    @Transactional
    fun registerDevice(userId: Long, fingerprint: String, deviceName: String?, deviceType: String?,
                       browserName: String?, osName: String?, ipAddress: String?): UserDeviceEntity {
        // Check existing device by fingerprint
        val existing = entityManager
            .createQuery("SELECT d FROM UserDeviceEntity d WHERE d.userId = :userId AND d.fingerprint = :fp AND d.active = true", UserDeviceEntity::class.java)
            .setParameter("userId", userId)
            .setParameter("fp", fingerprint)
            .resultList
            .firstOrNull()

        if (existing != null) {
            existing.lastUsedAt = Instant.now()
            existing.ipAddress = ipAddress
            return entityManager.merge(existing)
        }

        // Check max devices limit
        val count = entityManager
            .createQuery("SELECT COUNT(d) FROM UserDeviceEntity d WHERE d.userId = :userId AND d.active = true", Long::class.javaObjectType)
            .setParameter("userId", userId)
            .singleResult
        if (count >= maxDevicesPerUser) {
            throw AccountException(AccountErrorCode.MAX_DEVICES_REACHED, "Maximum devices ($maxDevicesPerUser) reached for user")
        }

        val device = UserDeviceEntity().apply {
            this.userId = userId
            this.fingerprint = fingerprint
            this.deviceName = deviceName
            this.deviceType = deviceType
            this.browserName = browserName
            this.osName = osName
            this.ipAddress = ipAddress
            this.lastUsedAt = Instant.now()
        }
        entityManager.persist(device)
        log.info("New device registered: userId={}, fingerprint={}", userId, fingerprint)
        return device
    }

    private fun findDeviceByIdAndUser(deviceId: Long, userId: Long): UserDeviceEntity? {
        return entityManager
            .createQuery("SELECT d FROM UserDeviceEntity d WHERE d.id = :id AND d.userId = :userId AND d.active = true", UserDeviceEntity::class.java)
            .setParameter("id", deviceId)
            .setParameter("userId", userId)
            .resultList
            .firstOrNull()
    }
}
