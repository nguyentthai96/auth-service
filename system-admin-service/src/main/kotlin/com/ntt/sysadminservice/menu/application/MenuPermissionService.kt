package com.ntt.sysadminservice.menu.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.shared.util.TreeBuilder
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Menu permission service — circular reference detection & validation (FR-010).
 * DFS cycle detection via TreeBuilder.detectCycle(), max depth check before save.
 * 
 * ⚠️ Assumption: MenuEntity already exists in system-admin-service codebase.
 * This modification adds cycle detection and max-depth validation.
 */
@Service
class MenuPermissionService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(MenuPermissionService::class.java)

    /**
     * Validate menu tree integrity before save.
     * Checks for circular references and max depth violations.
     */
    @Transactional
    fun validateAndSaveMenu(menuId: Long?, parentId: Long?, domainId: Long) {
        if (parentId != null) {
            // Cycle detection
            if (menuId != null && menuId == parentId) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_REFERENCE, "Menu cannot be its own parent")
            }

            val hasCycle = TreeBuilder.detectCycle(
                nodeId = menuId ?: -1,
                parentId = parentId,
                getParent = { id -> getMenuParentId(id) }
            )
            if (hasCycle) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_REFERENCE, "Circular reference detected in menu tree")
            }

            // Max depth check
            val parentDepth = getMenuDepth(parentId)
            val newDepth = parentDepth + 1
            if (newDepth > TreeBuilder.MAX_DEPTH) {
                throw SysAdminException(SysAdminErrorCode.MAX_DEPTH_EXCEEDED, "Menu tree depth exceeds maximum (${TreeBuilder.MAX_DEPTH})")
            }
        }

        log.debug("Menu validation passed: menuId={}, parentId={}", menuId, parentId)
    }

    /**
     * Batch validate all menus in a domain for consistency.
     */
    fun validateDomainMenuTree(domainId: Long): List<String> {
        val errors = mutableListOf<String>()

        // Check for orphan menus (parent doesn't exist)
        @Suppress("UNCHECKED_CAST")
        val orphans = entityManager
            .createNativeQuery("""
                SELECT m.id, m.name FROM menus m 
                WHERE m.domain_id = :domainId AND m.parent_id IS NOT NULL 
                AND m.parent_id NOT IN (SELECT id FROM menus WHERE domain_id = :domainId AND active = true)
                AND m.active = true
            """)
            .setParameter("domainId", domainId)
            .resultList as List<Array<Any>>

        orphans.forEach { row ->
            errors.add("Orphan menu: id=${row[0]}, name=${row[1]}")
        }

        return errors
    }

    private fun getMenuParentId(menuId: Long): Long? {
        return try {
            entityManager
                .createNativeQuery("SELECT parent_id FROM menus WHERE id = :id AND active = true")
                .setParameter("id", menuId)
                .singleResult as? Long
        } catch (e: Exception) {
            null
        }
    }

    private fun getMenuDepth(menuId: Long): Int {
        var depth = 0
        var current: Long? = menuId
        val visited = mutableSetOf<Long>()
        while (current != null && visited.add(current)) {
            depth++
            current = getMenuParentId(current)
        }
        return depth
    }
}
