package com.ntt.authservice.auth.application.pipeline

/**
 * Interface for a single step in the authentication pipeline.
 * Each step implements Chain of Responsibility pattern with immutable context propagation.
 *
 * FR-002: Steps are auto-discovered by Spring (List<AuthenticationStep>), sorted by [order],
 * and executed sequentially by [AuthenticationPipeline].
 *
 * Contract:
 * - [order] determines execution sequence (lower = earlier)
 * - [name] used for logging and debugging
 * - [execute] must return [StepOutcome.Continue] to proceed or [StepOutcome.ShortCircuit] to stop
 * - Steps MUST NOT modify the context — return new context via StepOutcome.Continue
 */
interface AuthenticationStep {

    /** Execution order (lower = earlier). Gaps of 100 recommended for extensibility. */
    val order: Int

    /** Human-readable name for logging and debugging. */
    val name: String

    /**
     * Execute this step with the given context.
     * @param context immutable authentication state from previous steps
     * @return Continue with updated context, or ShortCircuit with final result
     */
    fun execute(context: AuthenticationContext): StepOutcome
}
