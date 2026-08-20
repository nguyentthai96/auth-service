package com.ntt.sysadminservice.organization.application

import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.DepartmentEntity
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.shared.util.TreeBuilder
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Organization service — department tree management (FR-011).
 * Recursive CTE for tree queries, DFS cycle detection, max 10 levels.
 */
@Service
class OrganizationService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(OrganizationService::class.java)

    /**
     * Get full department tree for a domain.
     */
    fun getDepartmentTree(domainId: Long): List<DepartmentEntity> {
        val departments = entityManager
            .createQuery("SELECT d FROM DepartmentEntity d WHERE d.domainId = :domainId AND d.active = true ORDER BY d.sortOrder", DepartmentEntity::class.java)
            .setParameter("domainId", domainId)
            .resultList

        return buildDepartmentTree(departments)
    }

    /**
     * Create a new department.
     */
    @Transactional
    fun createDepartment(domainId: Long, code: String, name: String, parentId: Long?, description: String?): DepartmentEntity {
        // Validate parent exists and check depth
        var parentLevel = 0
        var parentPath: String? = null
        if (parentId != null) {
            val parent = findById(parentId)
                ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Parent department not found: $parentId")
            parentLevel = parent.treeLevel
            parentPath = parent.treePath

            // Cycle detection
            if (TreeBuilder.detectCycle(parentId, parentId, { pid -> findById(pid)?.parentId })) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_HIERARCHY, "Circular hierarchy detected")
            }
        }

        val newLevel = parentLevel + 1
        TreeBuilder.validateMaxDepth(newLevel)

        val dept = DepartmentEntity().apply {
            this.domainId = domainId
            this.code = code
            this.name = name
            this.parentId = parentId
            this.description = description
            this.treeLevel = newLevel
        }
        entityManager.persist(dept)
        dept.treePath = TreeBuilder.calculateTreePath(dept.id!!, parentPath)
        entityManager.merge(dept)

        log.info("Department created: id={}, code={}, domain={}", dept.id, code, domainId)
        return dept
    }

    /**
     * Update a department.
     */
    @Transactional
    fun updateDepartment(id: Long, name: String?, description: String?): DepartmentEntity {
        val dept = findById(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $id")

        name?.let { dept.name = it }
        description?.let { dept.description = it }

        return entityManager.merge(dept)
    }

    /**
     * Move a department to a new parent.
     */
    @Transactional
    fun moveDepartment(id: Long, newParentId: Long?): DepartmentEntity {
        val dept = findById(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $id")

        if (newParentId != null) {
            // Cycle detection: newParentId must not be a descendant of id
            if (TreeBuilder.detectCycle(id, newParentId, { pid -> findById(pid)?.parentId })) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_HIERARCHY, "Moving to a descendant creates a cycle")
            }
            val newParent = findById(newParentId)
                ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "New parent not found: $newParentId")
            val newLevel = newParent.treeLevel + 1
            TreeBuilder.validateMaxDepth(newLevel)
            dept.parentId = newParentId
            dept.treeLevel = newLevel
            dept.treePath = TreeBuilder.calculateTreePath(dept.id!!, newParent.treePath)
        } else {
            dept.parentId = null
            dept.treeLevel = 0
            dept.treePath = "/${dept.id}/"
        }

        log.info("Department moved: id={}, newParent={}", id, newParentId)
        return entityManager.merge(dept)
    }

    /**
     * Soft-delete a department.
     */
    @Transactional
    fun deleteDepartment(id: Long) {
        val dept = findById(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $id")

        // Check if has children
        val childCount = entityManager
            .createQuery("SELECT COUNT(d) FROM DepartmentEntity d WHERE d.parentId = :id AND d.active = true", Long::class.javaObjectType)
            .setParameter("id", id)
            .singleResult
        if (childCount > 0) {
            throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Cannot delete department with children. Move or delete children first.")
        }

        dept.active = false
        entityManager.merge(dept)
        log.info("Department deleted: id={}", id)
    }

    private fun findById(id: Long): DepartmentEntity? {
        return entityManager.find(DepartmentEntity::class.java, id)?.takeIf { it.active }
    }

    private fun buildDepartmentTree(departments: List<DepartmentEntity>): List<DepartmentEntity> {
        val byParent = departments.groupBy { it.parentId }
        val roots = byParent[null] ?: emptyList()

        fun attachChildren(parent: DepartmentEntity) {
            parent.children = (byParent[parent.id] ?: emptyList()).toMutableList()
            parent.children.forEach { attachChildren(it) }
        }

        roots.forEach { attachChildren(it) }
        return roots
    }
}
