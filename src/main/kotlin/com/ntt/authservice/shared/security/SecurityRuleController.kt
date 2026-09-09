package com.ntt.authservice.shared.security

import com.ntt.authservice.shared.security.entity.EndpointSecurityRuleEntity
import com.ntt.authservice.shared.security.repository.EndpointSecurityRuleRepository
import com.ntt.authservice.shared.exception.ResourceNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/**
 * Admin API for managing endpoint security rules at runtime.
 *
 * All changes are:
 * 1. Persisted to DB
 * 2. Published to Redis pub/sub (cache invalidation across instances)
 * 3. Logged via AuditLogService
 */
@RestController
@RequestMapping("/admin/security-rules")
class SecurityRuleController(
    private val ruleRepository: EndpointSecurityRuleRepository,
    private val dynamicAuthorizationManager: DynamicAuthorizationManager,
    private val redisTemplate: StringRedisTemplate?
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listRules(): ResponseEntity<List<SecurityRuleResponse>> {
        val rules = ruleRepository.findAllActiveOrderBySortOrder()
        return ResponseEntity.ok(rules.map { it.toResponse() })
    }

    @PostMapping
    @Transactional
    fun createRule(@Valid @RequestBody request: SecurityRuleCreateRequest): ResponseEntity<SecurityRuleResponse> {
        val rule = EndpointSecurityRuleEntity().apply {
            urlPattern = request.urlPattern
            patternType = request.patternType
            httpMethod = request.httpMethod
            accessType = request.accessType
            requiredRoles = request.requiredRoles?.toTypedArray()
            requiredPerms = request.requiredPerms?.toTypedArray()
            domainScope = request.domainScope
            sortOrder = request.sortOrder
            description = request.description
            createdBy = getCurrentUsername()
            updatedBy = getCurrentUsername()
        }
        val saved = ruleRepository.save(rule)
        publishCacheInvalidation("CREATED", saved.id)
        log.info("Security rule created: id={}, pattern={}", saved.id, saved.urlPattern)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    @PutMapping("/{ruleId}")
    @Transactional
    fun updateRule(
        @PathVariable ruleId: Long,
        @Valid @RequestBody request: SecurityRuleUpdateRequest
    ): ResponseEntity<SecurityRuleResponse> {
        val rule = ruleRepository.findById(ruleId).orElseThrow {
            ResourceNotFoundException("SecurityRule", ruleId)
        }
        request.urlPattern?.let { rule.urlPattern = it }
        request.patternType?.let { rule.patternType = it }
        request.httpMethod?.let { rule.httpMethod = it }
        request.accessType?.let { rule.accessType = it }
        request.requiredRoles?.let { rule.requiredRoles = it.toTypedArray() }
        request.requiredPerms?.let { rule.requiredPerms = it.toTypedArray() }
        request.domainScope?.let { rule.domainScope = it }
        request.sortOrder?.let { rule.sortOrder = it }
        request.description?.let { rule.description = it }
        request.isActive?.let { rule.isActive = it }
        rule.updatedBy = getCurrentUsername()

        val saved = ruleRepository.save(rule)
        publishCacheInvalidation("UPDATED", saved.id)
        log.info("Security rule updated: id={}, pattern={}", saved.id, saved.urlPattern)
        return ResponseEntity.ok(saved.toResponse())
    }

    @DeleteMapping("/{ruleId}")
    @Transactional
    fun deleteRule(@PathVariable ruleId: Long): ResponseEntity<Void> {
        val rule = ruleRepository.findById(ruleId).orElseThrow {
            ResourceNotFoundException("SecurityRule", ruleId)
        }
        rule.isActive = false
        rule.updatedBy = getCurrentUsername()
        ruleRepository.save(rule)
        publishCacheInvalidation("DELETED", ruleId)
        log.info("Security rule soft-deleted: id={}", ruleId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/test")
    fun testRule(@Valid @RequestBody request: TestRuleRequest): ResponseEntity<TestRuleResponse> {
        val rules = ruleRepository.findAllActiveOrderBySortOrder()
        val matched = rules.firstOrNull { rule ->
            matchesPath(rule, request.path, request.method)
        }
        return ResponseEntity.ok(TestRuleResponse(
            path = request.path,
            method = request.method,
            matchedRule = matched?.toResponse(),
            accessDecision = matched?.accessType ?: "AUTHENTICATED (default)"
        ))
    }

    @PostMapping("/reload")
    fun reloadCache(): ResponseEntity<Map<String, String>> {
        dynamicAuthorizationManager.evictCache()
        publishCacheInvalidation("RELOAD", null)
        return ResponseEntity.ok(mapOf("message" to "Cache reloaded on all instances"))
    }

    private fun matchesPath(rule: EndpointSecurityRuleEntity, path: String, method: String?): Boolean {
        if (rule.httpMethod != null && method != null && !rule.httpMethod.equals(method, ignoreCase = true)) {
            return false
        }
        return when (rule.patternType) {
            "EXACT" -> path == rule.urlPattern
            "ANT" -> org.springframework.util.AntPathMatcher().match(rule.urlPattern, path)
            "REGEX" -> Regex(rule.urlPattern).matches(path)
            else -> false
        }
    }

    private fun publishCacheInvalidation(action: String, ruleId: Long?) {
        try {
            val message = """{"action":"$action","ruleId":${ruleId ?: "null"},"timestamp":"${java.time.Instant.now()}"}"""
            redisTemplate?.convertAndSend(SecurityRuleCacheConfig.CHANNEL, message)
            // Also evict local cache immediately
            dynamicAuthorizationManager.evictCache()
        } catch (e: Exception) {
            log.warn("Failed to publish cache invalidation to Redis, local cache evicted only", e)
            dynamicAuthorizationManager.evictCache()
        }
    }

    private fun getCurrentUsername(): String {
        return SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM"
    }
}

// ============================================================
// DTOs
// ============================================================

data class SecurityRuleCreateRequest(
    @field:NotBlank val urlPattern: String,
    val patternType: String = "ANT",
    val httpMethod: String? = null,
    val accessType: String = "AUTHENTICATED",
    val requiredRoles: List<String>? = null,
    val requiredPerms: List<String>? = null,
    val domainScope: String? = null,
    val sortOrder: Int = 0,
    val description: String? = null
)

data class SecurityRuleUpdateRequest(
    val urlPattern: String? = null,
    val patternType: String? = null,
    val httpMethod: String? = null,
    val accessType: String? = null,
    val requiredRoles: List<String>? = null,
    val requiredPerms: List<String>? = null,
    val domainScope: String? = null,
    val sortOrder: Int? = null,
    val description: String? = null,
    val isActive: Boolean? = null
)

data class SecurityRuleResponse(
    val id: Long,
    val urlPattern: String,
    val patternType: String,
    val httpMethod: String?,
    val accessType: String,
    val requiredRoles: List<String>?,
    val requiredPerms: List<String>?,
    val domainScope: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    val description: String?
)

fun EndpointSecurityRuleEntity.toResponse() = SecurityRuleResponse(
    id = id!!,
    urlPattern = urlPattern,
    patternType = patternType,
    httpMethod = httpMethod,
    accessType = accessType,
    requiredRoles = requiredRoles?.toList(),
    requiredPerms = requiredPerms?.toList(),
    domainScope = domainScope,
    sortOrder = sortOrder,
    isActive = isActive,
    description = description
)

data class TestRuleRequest(
    val path: String,
    val method: String? = null
)

data class TestRuleResponse(
    val path: String,
    val method: String?,
    val matchedRule: SecurityRuleResponse?,
    val accessDecision: String
)
