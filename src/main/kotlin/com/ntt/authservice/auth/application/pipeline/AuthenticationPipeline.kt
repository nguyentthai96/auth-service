package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.command.LoginCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Authentication pipeline orchestrator — Chain of Responsibility.
 * FR-001: Executes all AuthenticationStep implementations in order.
 *
 * Steps are auto-discovered by Spring (List<AuthenticationStep>) and sorted by order.
 * Pipeline uses immutable context propagation (fold pattern).
 *
 * Short-circuit: Any step can return StepOutcome.ShortCircuit to stop early (e.g., MFA required).
 */
@Component
class AuthenticationPipeline(steps: List<AuthenticationStep>) {

    private val log = LoggerFactory.getLogger(AuthenticationPipeline::class.java)
    private val sortedSteps = steps.sortedBy { it.order }

    init {
        log.info(
            "Authentication pipeline initialized with {} steps: [{}]",
            sortedSteps.size,
            sortedSteps.joinToString(" → ") { "${it.name}(${it.order})" }
        )
    }

    /**
     * Execute the authentication pipeline for a login command.
     *
     * @param command the login command
     * @return LoginResult from the final step or short-circuit
     * @throws Exception if any step throws (exception propagated to caller)
     */
    fun execute(command: LoginCommand): LoginResult {
        var context = AuthenticationContext(command = command)

        for (step in sortedSteps) {
            log.debug("Executing step: {} (order={})", step.name, step.order)

            when (val outcome = step.execute(context)) {
                is StepOutcome.Continue -> {
                    context = outcome.context
                    log.debug("Step {} completed — continuing", step.name)
                }
                is StepOutcome.ShortCircuit -> {
                    log.info("Step {} short-circuited pipeline with: {}", step.name, outcome.result::class.simpleName)
                    return outcome.result
                }
            }
        }

        // All steps completed — build final result from context
        val authResponse = context.authResponse
            ?: throw IllegalStateException("Pipeline completed but no auth response was generated")

        return LoginResult.Success(
            response = authResponse,
            promotionResult = context.promotionResult
        )
    }
}
