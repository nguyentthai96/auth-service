package com.ntt.auth.client.repository

import com.ntt.auth.client.entity.GroupReadModel
import com.ntt.auth.client.entity.RoleReadModel
import com.ntt.auth.client.entity.UserReadModel
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

/**
 * Read-only repository for user lookups.
 * Extends marker Repository interface — no save/delete methods.
 */
interface UserReadRepository : Repository<UserReadModel, Long> {
    fun findById(id: Long): UserReadModel?
    fun findByUsername(username: String): UserReadModel?
    fun findAllByIdIn(ids: List<Long>): List<UserReadModel>
}

/**
 * Read-only repository for roles.
 */
interface RoleReadRepository : Repository<RoleReadModel, Long> {
    fun findById(id: Long): RoleReadModel?
    fun findByCode(code: String): RoleReadModel?

    /**
     * Find all roles assigned to a user via user_groups → group_roles.
     */
    @Query("""
        SELECT DISTINCT r FROM RoleReadModel r
        WHERE r.id IN (
            SELECT gr.roleId FROM com.ntt.auth.client.entity.GroupRoleReadModel gr
            WHERE gr.groupId IN (
                SELECT ug.groupId FROM com.ntt.auth.client.entity.UserGroupReadModel ug
                WHERE ug.userId = :userId
            )
        )
    """)
    fun findRolesByUserId(@Param("userId") userId: Long): List<RoleReadModel>
}

/**
 * Read-only permission queries — resolves user → groups → roles → permissions chain.
 */
interface PermissionReadRepository : Repository<GroupReadModel, Long> {

    /**
     * Find all permission codes for a user (resource.code + ':' + action.code).
     */
    @Query("""
        SELECT DISTINCT CONCAT(res.code, ':', act.code) FROM com.ntt.auth.client.entity.RolePermissionReadModel rp
        JOIN com.ntt.auth.client.entity.PermissionReadModel p ON p.id = rp.permissionId
        JOIN com.ntt.auth.client.entity.ResourceReadModel res ON res.id = p.resourceId
        JOIN com.ntt.auth.client.entity.ActionReadModel act ON act.id = p.actionId
        WHERE rp.roleId IN (
            SELECT gr.roleId FROM com.ntt.auth.client.entity.GroupRoleReadModel gr
            WHERE gr.groupId IN (
                SELECT ug.groupId FROM com.ntt.auth.client.entity.UserGroupReadModel ug
                WHERE ug.userId = :userId
            )
        )
    """)
    fun findPermissionCodesByUserId(@Param("userId") userId: Long): Set<String>
}
