package com.ntt.authservice.shared.persistence

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version

/**
 * Opt-in versioned entity — adds @Version for optimistic locking.
 *
 * User preference: "chỉ một vài bảng quan trọng mới cần version optimistic locking thôi"
 * Only entities that need concurrent write protection should extend this class.
 *
 * Usage: `class UserEntity : VersionedAuditableEntity()` instead of `SnowflakePersistentAuditableEntity()`
 *
 * NOTE: This is placed in auth-service for now.
 * TODO: Move to base-model as Task 23 specifies once base-core source is available.
 */
@MappedSuperclass
open class VersionedAuditableEntity : SnowflakePersistentAuditableEntity() {

    @Version
    var version: Long = 0
}
