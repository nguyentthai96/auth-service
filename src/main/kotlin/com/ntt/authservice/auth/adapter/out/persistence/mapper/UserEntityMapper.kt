package com.ntt.authservice.auth.adapter.out.persistence.mapper

import com.ntt.authservice.auth.domain.model.User
import com.ntt.authservice.auth.domain.model.UserStatus
import com.ntt.authservice.auth.domain.model.vo.Email
import com.ntt.authservice.auth.domain.model.vo.PasswordHash
import com.ntt.authservice.auth.domain.model.vo.UserId
import com.ntt.authservice.rbac.adapter.out.persistence.entity.UserEntity

/**
 * Manual mapping between UserEntity (JPA) ↔ User (domain).
 * Kotlin extension functions replace MapStruct — simpler, no code generation.
 */

fun UserEntity.toDomain() = User(
    id = UserId(this.id ?: 0L),
    username = this.username ?: "",
    email = Email(this.email ?: ""),
    passwordHash = PasswordHash(this.password ?: ""),
    fullName = this.fullName ?: "",
    phone = this.phone,
    avatarUrl = this.avatarUrl,
    status = UserStatus.fromString(this.status ?: "ACTIVE", this.lockedUntilAt),
    failedLoginCount = this.failedLoginCount ?: 0,
    lockedUntilAt = this.lockedUntilAt,
    mfaEnabled = this.mfaEnabled ?: false,
    mfaMethod = this.mfaMethod ?: "NONE",
    trustedDeviceHash = this.trustedDeviceHash,
    passwordChangedAt = this.passwordChangedAt,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)

fun User.toEntity(existing: UserEntity? = null): UserEntity {
    val entity = existing ?: UserEntity()
    entity.apply {
        if (!this@toEntity.id.isNew) this.id = this@toEntity.id.value
        username = this@toEntity.username
        email = this@toEntity.email.value
        password = this@toEntity.passwordHash.value
        fullName = this@toEntity.fullName
        phone = this@toEntity.phone
        avatarUrl = this@toEntity.avatarUrl
        status = UserStatus.toDbString(this@toEntity.status)
        failedLoginCount = this@toEntity.failedLoginCount
        lockedUntilAt = this@toEntity.lockedUntilAt
        mfaEnabled = this@toEntity.mfaEnabled
        mfaMethod = this@toEntity.mfaMethod
        trustedDeviceHash = this@toEntity.trustedDeviceHash
        passwordChangedAt = this@toEntity.passwordChangedAt
    }
    return entity
}
