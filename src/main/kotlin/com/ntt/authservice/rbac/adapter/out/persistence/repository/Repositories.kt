package com.ntt.authservice.rbac.adapter.out.persistence.repository

import com.ntt.authservice.rbac.adapter.out.persistence.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByUsernameAndActiveTrue(username: String): UserEntity?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}

@Repository
interface DomainRepository : JpaRepository<DomainEntity, Long> {
    fun findByCodeAndActiveTrue(code: String): DomainEntity?
    fun findAllByActiveTrue(): List<DomainEntity>
    fun existsByCode(code: String): Boolean
}

@Repository
interface UserDomainRepository : JpaRepository<UserDomainEntity, Long> {
    fun findAllByUserIdAndActiveTrue(userId: Long): List<UserDomainEntity>
    fun findByUserIdAndDomainIdAndActiveTrue(userId: Long, domainId: Long): UserDomainEntity?
}

@Repository
interface GroupRepository : JpaRepository<GroupEntity, Long> {
    fun findAllByDomainIdAndActiveTrue(domainId: Long): List<GroupEntity>
}

@Repository
interface UserGroupRepository : JpaRepository<UserGroupEntity, Long> {
    fun findAllByUserIdAndActiveTrue(userId: Long): List<UserGroupEntity>
}

@Repository
interface DomainRoleRepository : JpaRepository<DomainRoleEntity, Long> {
    fun findAllByDomainIdAndActiveTrue(domainId: Long): List<DomainRoleEntity>
    fun findByDomainIdAndCodeAndActiveTrue(domainId: Long, code: String): DomainRoleEntity?
}

@Repository
interface GroupRoleRepository : JpaRepository<GroupRoleEntity, Long> {
    fun findAllByGroupIdAndActiveTrue(groupId: Long): List<GroupRoleEntity>
}

@Repository
interface ActionRepository : JpaRepository<ActionEntity, Long> {
    fun findByCode(code: String): ActionEntity?
}

@Repository
interface DomainResourceRepository : JpaRepository<DomainResourceEntity, Long> {
    fun findAllByDomainIdAndActiveTrue(domainId: Long): List<DomainResourceEntity>
    fun findByDomainIdAndCodeAndActiveTrue(domainId: Long, code: String): DomainResourceEntity?
}

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, Long> {
    fun findByResourceIdAndActionId(resourceId: Long, actionId: Long): PermissionEntity?
    fun findAllByResourceId(resourceId: Long): List<PermissionEntity>
}

@Repository
interface RolePermissionRepository : JpaRepository<RolePermissionEntity, Long> {
    fun findAllByRoleIdAndActiveTrue(roleId: Long): List<RolePermissionEntity>
}

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByTokenHashAndRevokedFalse(tokenHash: String): RefreshTokenEntity?
}

@Repository
interface TokenBlacklistRepository : JpaRepository<TokenBlacklistEntity, Long> {
    fun existsByTokenJti(tokenJti: String): Boolean
}
