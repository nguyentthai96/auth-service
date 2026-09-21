package com.ntt.authservice.rbac.adapter.`in`.web

import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.web.BaseController
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Internal API controller for user data access by other services (full-text-user-search).
 * Protected by ServiceAuthFilter — requires service JWT for inter-service calls.
 *
 * Used by account-service during full reindex to fetch all user data.
 */
@RestController
@RequestMapping("/api/internal/users")
class InternalUserController(
    private val userRepository: UserRepository,
    @PersistenceContext
    private val entityManager: EntityManager
) : BaseController() {

    /**
     * Get paginated list of all active users with their roles.
     * Used for search index full reindex.
     *
     * @param page Page number (0-based)
     * @param size Page size (default 500, max 1000)
     * @return Paginated user list with roles
     */
    @GetMapping
    fun getAllUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "500") size: Int
    ): ResponseEntity<InternalUserPageResponse> {
        val effectiveSize = size.coerceIn(1, 1000)
        val pageable = PageRequest.of(page, effectiveSize, Sort.by("id").ascending())

        val usersPage = userRepository.findAll(pageable)

        val userResponses = usersPage.content.map { user ->
            val roles = getUserRoles(user.id!!)

            InternalUserResponse(
                userId = user.id!!,
                username = user.username,
                email = user.email,
                fullName = user.fullName,
                phone = user.phone,
                status = user.status,
                avatarUrl = user.avatarUrl,
                roles = roles,
                createdAt = user.createdAt?.toString()
            )
        }

        return ResponseEntity.ok(
            InternalUserPageResponse(
                content = userResponses,
                page = usersPage.number,
                size = usersPage.size,
                totalElements = usersPage.totalElements,
                totalPages = usersPage.totalPages,
                last = usersPage.isLast
            )
        )
    }

    /**
     * Get roles for a user through the RBAC chain: User → UserGroup → GroupRole → DomainRole.
     */
    private fun getUserRoles(userId: Long): List<String> {
        val sql = """
            SELECT DISTINCT dr.name
            FROM user_groups ug
            JOIN group_roles gr ON gr.group_id = ug.group_id AND gr.active = true
            JOIN domain_roles dr ON dr.id = gr.role_id AND dr.active = true
            WHERE ug.user_id = :userId AND ug.active = true
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val results = entityManager.createNativeQuery(sql)
            .setParameter("userId", userId)
            .resultList as List<String>

        return results
    }
}

data class InternalUserResponse(
    val userId: Long,
    val username: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val status: String,
    val avatarUrl: String?,
    val roles: List<String>,
    val createdAt: String?
)

data class InternalUserPageResponse(
    val content: List<InternalUserResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean
)
