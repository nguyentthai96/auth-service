package com.ntt.authservice.auth.application.pipeline

import com.ntt.authservice.auth.application.LoginResult
import com.ntt.authservice.auth.application.PasswordPolicyService
import com.ntt.authservice.auth.application.SessionPolicyService
import com.ntt.authservice.auth.application.command.TokenGenerator
import com.ntt.authservice.rbac.application.query.GetUserRolesHandler
import com.ntt.authservice.rbac.application.query.GetUserRolesQuery
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.PasswordExpiredException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Step 3: Policy enforcement — password expiry, MFA, session policy.
 * FR-006: Password expiry check, MFA checkpoint, session policy enforcement.
 *
 * May short-circuit pipeline with MfaRequired result.
 * Domain logic removed — global RBAC scope.
 */
@Component
class PolicyEnforcementStep(
    private val passwordPolicyService: PasswordPolicyService,
    private val securityProperties: SecurityProperties,
    private val sessionPolicyService: SessionPolicyService,
    private val getUserRolesHandler: GetUserRolesHandler,
    private val tokenGenerator: TokenGenerator
) : AuthenticationStep {

    private val log = LoggerFactory.getLogger(PolicyEnforcementStep::class.java)

    override val order: Int = 300
    override val name: String = "PolicyEnforcement"

    override fun execute(context: AuthenticationContext): StepOutcome {
        val command = context.command
        val user = context.user
            ?: throw IllegalStateException("User not resolved in previous step")

        // Password expiry check (global scope)
        if (passwordPolicyService.isPasswordExpired(user.id.value)) {
            throw PasswordExpiredException()
        }

        // MFA checkpoint — may short-circuit
        if (user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)) {
            log.info("MFA required for user: {}", user.username)
            return StepOutcome.ShortCircuit(
                tokenGenerator.generateMfaResult(user.id.value, user.mfaMethod)
            )
        }

        // Load roles (global scope)
        val roles = getUserRolesHandler.handle(GetUserRolesQuery(user.id.value))

        // Enforce session policy (may revoke oldest or throw)
        sessionPolicyService.enforcePolicy(user.id.value, roles)

        log.debug("Policy enforcement passed for user: {}", user.username)
        return StepOutcome.Continue(
            context.copy(
                roles = roles
            )
        )
    }
}
