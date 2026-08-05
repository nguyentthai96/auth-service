package com.ntt.authservice.pbac.application

import com.ntt.authservice.pbac.adapter.out.persistence.entity.PolicyConditionEntity
import com.ntt.authservice.pbac.adapter.out.persistence.entity.PolicyEntity
import com.ntt.authservice.pbac.adapter.out.persistence.repository.PolicyRepository
import com.ntt.authservice.shared.exception.PolicyEvaluationException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.*

/**
 * PBAC Policy Evaluator — Stage 2 authorization.
 * Evaluates dynamic policy conditions after RBAC check passes.
 *
 * Inspired by thunx authorization predicate concept:
 * - Evaluates JSONB conditions against user attributes and request context
 * - DENY policies take precedence over ALLOW (BR-003)
 * - Has 500ms timeout to prevent hanging (flow-logic-review finding)
 */
@Service
class PolicyEvaluator(
    private val policyRepository: PolicyRepository,
    private val jsonMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(PolicyEvaluator::class.java)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    companion object {
        const val EVALUATION_TIMEOUT_MS = 500L
    }

    /**
     * Evaluate all active policies for a given resource:action in domain.
     *
     * @param domainId the domain scope
     * @param resourceId the target resource (nullable = apply to all)
     * @param actionId the target action (nullable = apply to all)
     * @param userAttributes user context (id, groups, roles, etc.)
     * @param requestContext request context (ip, time, custom fields)
     * @return true if allowed, false if denied
     */
    fun evaluate(
        domainId: Long,
        resourceId: Long?,
        actionId: Long?,
        userAttributes: Map<String, Any>,
        requestContext: Map<String, Any> = emptyMap()
    ): Boolean {
        val policies = policyRepository.findApplicablePolicies(domainId, resourceId, actionId)

        if (policies.isEmpty()) {
            log.debug("No policies found for domain={} resource={} action={}", domainId, resourceId, actionId)
            return true // No policies = allow (RBAC already passed)
        }

        // Evaluate with timeout (500ms max per flow-logic-review)
        val future: Future<Boolean> = executor.submit(Callable {
            evaluatePolicies(policies, userAttributes, requestContext)
        })

        return try {
            future.get(EVALUATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.warn("Policy evaluation timed out for domain={}", domainId)
            future.cancel(true)
            throw PolicyEvaluationException("Policy evaluation timed out after ${EVALUATION_TIMEOUT_MS}ms")
        }
    }

    private fun evaluatePolicies(
        policies: List<PolicyEntity>,
        userAttributes: Map<String, Any>,
        requestContext: Map<String, Any>
    ): Boolean {
        // Sort by priority (lower number = higher priority)
        val sorted = policies.sortedBy { it.priority }

        for (policy in sorted) {
            val conditionsMet = policy.conditions.all { condition ->
                evaluateCondition(condition, userAttributes, requestContext)
            }

            if (conditionsMet) {
                // BR-003: DENY always wins
                if (policy.effect == "DENY") {
                    log.debug("PBAC DENY: policy={} matched", policy.name)
                    return false
                }
            }
        }

        // If no DENY matched and at least one ALLOW matched
        val anyAllowMatched = sorted.any { policy ->
            policy.effect == "ALLOW" && policy.conditions.all { condition ->
                evaluateCondition(condition, userAttributes, requestContext)
            }
        }

        return anyAllowMatched || sorted.none { it.effect == "ALLOW" }
    }

    private fun evaluateCondition(
        condition: PolicyConditionEntity,
        userAttributes: Map<String, Any>,
        requestContext: Map<String, Any>
    ): Boolean {
        val actualValue = resolveAttributePath(condition.attributePath, userAttributes, requestContext)
        val expectedValue = parseJsonValue(condition.value)

        return when (condition.operator) {
            "eq" -> actualValue?.toString() == expectedValue?.toString()
            "neq" -> actualValue?.toString() != expectedValue?.toString()
            "in" -> {
                val set = (expectedValue as? List<*>)?.map { it.toString() } ?: emptyList()
                actualValue?.toString() in set
            }
            "not_in" -> {
                val set = (expectedValue as? List<*>)?.map { it.toString() } ?: emptyList()
                actualValue?.toString() !in set
            }
            "gt" -> compareNumbers(actualValue, expectedValue) > 0
            "gte" -> compareNumbers(actualValue, expectedValue) >= 0
            "lt" -> compareNumbers(actualValue, expectedValue) < 0
            "lte" -> compareNumbers(actualValue, expectedValue) <= 0
            "contains" -> actualValue?.toString()?.contains(expectedValue?.toString() ?: "") == true
            "starts_with" -> actualValue?.toString()?.startsWith(expectedValue?.toString() ?: "") == true
            else -> {
                log.warn("Unknown operator: {}", condition.operator)
                false
            }
        }
    }

    private fun resolveAttributePath(path: String, userAttrs: Map<String, Any>, context: Map<String, Any>): Any? {
        return when {
            path.startsWith("\$user.") -> {
                val key = path.removePrefix("\$user.")
                userAttrs[key]
            }
            path.startsWith("\$context.") -> {
                val key = path.removePrefix("\$context.")
                context[key]
            }
            path.startsWith("\$request.") -> {
                val key = path.removePrefix("\$request.")
                context[key]
            }
            else -> context[path] ?: userAttrs[path]
        }
    }

    private fun parseJsonValue(jsonStr: String): Any? {
        return try {
            val node: JsonNode = jsonMapper.readTree(jsonStr)
            when {
                node.isString -> node.asString()
                node.isNumber -> node.numberValue()
                node.isBoolean -> node.asBoolean()
                node.isArray -> node.map { if (it.isString) it.asString() else it.toString() }
                else -> node.toString()
            }
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun compareNumbers(a: Any?, b: Any?): Int {
        val numA = (a as? Number)?.toDouble() ?: a?.toString()?.toDoubleOrNull() ?: 0.0
        val numB = (b as? Number)?.toDouble() ?: b?.toString()?.toDoubleOrNull() ?: 0.0
        return numA.compareTo(numB)
    }
}
