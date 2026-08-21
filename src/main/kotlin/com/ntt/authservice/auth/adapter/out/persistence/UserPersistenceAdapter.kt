package com.ntt.authservice.auth.adapter.out.persistence

import com.ntt.authservice.auth.application.port.out.UserPort
import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import org.springframework.stereotype.Component

/**
 * JPA adapter implementing UserPort — converts between domain and entity.
 */
@Component
class UserPersistenceAdapter(
    private val userRepository: UserRepository
) : UserPort {

    override fun findById(userId: Long): User? {
        return userRepository.findById(userId).orElse(null)?.toDomain()
    }

    override fun findByUsernameAndActive(username: String): User? {
        return userRepository.findByUsernameAndActiveTrue(username)?.toDomain()
    }

    override fun existsByUsername(username: String): Boolean {
        return userRepository.existsByUsername(username)
    }

    override fun existsByEmail(email: String): Boolean {
        return userRepository.existsByEmail(email)
    }

    override fun save(user: User): User {
        val entity = if (user.id.value == 0L) {
            // New user — create entity
            UserEntity().apply {
                username = user.username
                email = user.email.value
                passwordHash = user.passwordHash.value
                fullName = user.fullName
                phone = user.phone
                status = UserStatus.toDbString(user.status)
                failedLoginCount = user.failedLoginCount
                lockedUntilAt = user.lockedUntilAt
            }
        } else {
            // Existing user — find and update
            userRepository.findById(user.id.value).orElseThrow().apply {
                status = UserStatus.toDbString(user.status)
                failedLoginCount = user.failedLoginCount
                lockedUntilAt = user.lockedUntilAt
                passwordHash = user.passwordHash.value
            }
        }
        return userRepository.save(entity).toDomain()
    }

    companion object {
        /**
         * Entity → Domain mapping.
         */
        fun UserEntity.toDomain(): User = User(
            id = UserId(this.id!!),
            username = this.username,
            email = Email(this.email),
            passwordHash = PasswordHash(this.passwordHash),
            fullName = this.fullName,
            phone = this.phone,
            avatarUrl = this.avatarUrl,
            status = UserStatus.fromString(this.status, this.lockedUntilAt),
            failedLoginCount = this.failedLoginCount,
            lockedUntilAt = this.lockedUntilAt,
            mfaEnabled = this.mfaEnabled,
            mfaMethod = this.mfaMethod,
            trustedDeviceHash = this.trustedDeviceHash,
            trustedDeviceSetAt = this.trustedDeviceSetAt,
            passwordChangedAt = this.passwordChangedAt,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
