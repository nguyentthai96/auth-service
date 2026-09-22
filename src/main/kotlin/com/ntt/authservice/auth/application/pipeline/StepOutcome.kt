package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.application.LoginResult

/**
 * Sealed outcome of an authentication pipeline step.
 *
 * FR-003: Two possible outcomes:
 * - [Continue]: step succeeded, pass updated context to next step
 * - [ShortCircuit]: step needs to stop the pipeline early (e.g., MFA required)
 */
sealed class StepOutcome {

    /** Step succeeded — continue pipeline with updated context. */
    data class Continue(val context: AuthenticationContext) : StepOutcome()

    /** Step needs to stop the pipeline — return this result immediately. */
    data class ShortCircuit(val result: LoginResult) : StepOutcome()
}
