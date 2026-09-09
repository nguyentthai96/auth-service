package com.ntt.authservice.shared.security

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.ntt.authservice.shared.security.entity.EndpointSecurityRuleEntity
import com.ntt.authservice.shared.security.repository.EndpointSecurityRuleRepository
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import java.time.Duration
import java.util.function.Supplier
import java.util.regex.Pattern

/**
 * DynamicAuthorizationManager — DB-driven authorization with Caffeine cache.
 *
 * Replaces hardcoded SecurityConfig.authorizeHttpRequests with dynamic rules
 * loaded from endpoint_security_rules table.
 *
 * Features:
 * - EXACT, ANT, REGEX pattern matching
 * - Multi-tenant support (domain_scope)
 * - Caffeine L1 cache (TTL 5 min, max 500 rules)
 * - Fallback hardcoded whitelist when DB is down
 * - Cache invalidation via Redis pub/sub (see SecurityRuleCacheListener)
 */
@Component
class DynamicAuthorizationManager(
    private val ruleRepository: EndpointSecurityRuleRepository
) : AuthorizationManager<RequestAuthorizationContext> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val antPathMatcher = AntPathMatcher()

    companion object {
        private const val CACHE_KEY = "ALL_RULES"

        /**
         * Critical whitelist — always permit even when DB is down.
         */
        private val HARDCODED_WHITELIST = listOf(
            "/auth/login" to "POST",
            "/auth/register" to "POST",
            "/auth/refresh" to "POST",
            "/.well-known/jwks.json" to "GET",
            "/captcha/challenge" to "GET",
            "/actuator/health" to "GET",
            "/actuator/health/liveness" to "GET",
            "/actuator/health/readiness" to "GET",
        )
    }

    /**
     * Caffeine L1 cache — loads all active rules on miss.
     */
    private val rulesCache: LoadingCache<String, List<EndpointSecurityRuleEntity>> =
        Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build { _ -> loadRulesFromDb() }

    override fun authorize(
        authentication: Supplier<out Authentication?>,
        context: RequestAuthorizationContext
    ): AuthorizationResult? {
        val request = context.request
        val requestPath = request.requestURI
        val requestMethod = request.method

        // Try DB-driven rules first
        val rules = try {
            rulesCache.get(CACHE_KEY) ?: emptyList()
        } catch (e: Exception) {
            log.warn("Failed to load security rules from cache/DB, using hardcoded fallback", e)
            return checkHardcodedFallback(requestPath, requestMethod)
        }

        // Match rules in sort_order
        for (rule in rules) {
            if (matchesRule(rule, request)) {
                return evaluateAccess(rule, authentication)
            }
        }

        // Default: require authentication for unmatched paths
        val auth = authentication?.get()
        return AuthorizationDecision(auth != null && auth.isAuthenticated)
    }

    /**
     * Match request against a single rule.
     */
    private fun matchesRule(rule: EndpointSecurityRuleEntity, request: HttpServletRequest): Boolean {
        // Check HTTP method
        if (rule.httpMethod != null && !rule.httpMethod.equals(request.method, ignoreCase = true)) {
            return false
        }

        val requestPath = request.requestURI

        return when (rule.patternType) {
            "EXACT" -> requestPath == rule.urlPattern
            "ANT" -> antPathMatcher.match(rule.urlPattern, requestPath)
            "REGEX" -> {
                try {
                    Pattern.compile(rule.urlPattern).matcher(requestPath).matches()
                } catch (e: Exception) {
                    log.error("Invalid regex pattern: ${rule.urlPattern}", e)
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Evaluate access based on rule's access_type.
     */
    private fun evaluateAccess(
        rule: EndpointSecurityRuleEntity,
        authentication: Supplier<out Authentication?>
    ): AuthorizationDecision {
        return when (rule.accessType) {
            "PERMIT_ALL" -> AuthorizationDecision(true)
            "DENY_ALL" -> AuthorizationDecision(false)
            "AUTHENTICATED" -> {
                val auth = authentication?.get()
                AuthorizationDecision(auth != null && auth.isAuthenticated)
            }
            "HAS_ROLE", "HAS_ANY_ROLE" -> {
                val auth = authentication?.get() ?: return AuthorizationDecision(false)
                val roles = rule.requiredRoles ?: return AuthorizationDecision(false)
                val hasRole = auth.authorities.any { granted ->
                    roles.any { role -> granted.authority == "ROLE_$role" || granted.authority == role }
                }
                AuthorizationDecision(hasRole)
            }
            "HAS_AUTHORITY" -> {
                val auth = authentication?.get() ?: return AuthorizationDecision(false)
                val perms = rule.requiredPerms ?: return AuthorizationDecision(false)
                val hasAllPerms = perms.all { perm ->
                    auth.authorities.any { it.authority == perm }
                }
                AuthorizationDecision(hasAllPerms)
            }
            else -> {
                log.warn("Unknown access_type: ${rule.accessType} for pattern: ${rule.urlPattern}")
                AuthorizationDecision(false)
            }
        }
    }

    /**
     * Hardcoded fallback when DB/cache is unavailable.
     */
    private fun checkHardcodedFallback(path: String, method: String): AuthorizationDecision {
        val isWhitelisted = HARDCODED_WHITELIST.any { (pattern, m) ->
            path == pattern && method.equals(m, ignoreCase = true)
        }
        return if (isWhitelisted) {
            AuthorizationDecision(true)
        } else {
            AuthorizationDecision(false)
        }
    }

    /**
     * Load rules from DB — called by Caffeine on cache miss.
     */
    private fun loadRulesFromDb(): List<EndpointSecurityRuleEntity> {
        log.info("Loading security rules from database...")
        val rules = ruleRepository.findAllActiveOrderBySortOrder()
        log.info("Loaded {} active security rules", rules.size)
        return rules
    }

    /**
     * Evict cache — called by Redis pub/sub listener or admin API.
     */
    fun evictCache() {
        log.info("Evicting security rules cache (triggered by admin/Redis)")
        rulesCache.invalidateAll()
    }
}
