package com.ntt.authservice.shared.exception

import com.ntt.basecore.domain.web.BaseControllerAdvice
import com.ntt.basecore.domain.web.payload.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * Auth-service controller advice — extends BaseControllerAdvice (base-core)
 * and adds AuthException handling with RFC 7807 ProblemDetail + proper HTTP status.
 *
 * Bridge pattern: AuthException → BusinessException (base-core) for cross-service consistency,
 * but auth-specific errors get ProblemDetail with AuthErrorCode and correct HTTP status
 * instead of base-core's default 422 UNPROCESSABLE_ENTITY.
 *
 * Priority: Spring resolves handlers from most specific to least specific:
 * 1. AuthException → handleAuthException() (this class — returns ProblemDetail with correct status)
 * 2. BusinessException → handleBusinessException() (BaseControllerAdvice — returns ApiResponse with 422)
 * 3. Throwable → handleException() (BaseControllerAdvice — fallback)
 */
@RestControllerAdvice
class AuthControllerAdvice(
    validator: LocalValidatorFactoryBean
) : BaseControllerAdvice(validator) {

    /**
     * Handle AuthException hierarchy with RFC 7807 ProblemDetail.
     * Overrides base-core's BusinessException handler for auth-specific exceptions
     * to return the correct HTTP status per exception type instead of 422.
     */
    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, ex.message)
        problem.title = ex.authError.getErrorCode()
        problem.type = URI.create("https://auth-service/errors/${ex.authError.name.lowercase()}")
        problem.setProperty("errorCode", ex.authError.getErrorCode())

        // Extra properties for specific exception types
        if (ex is AccountLockedException) {
            problem.setProperty("lockedUntilAt", ex.lockedUntilAt.toString())
        }
        if (ex is MfaAccountLockedException) {
            problem.setProperty("retryAfterSeconds", ex.retryAfterSeconds)
            problem.setProperty("lockType", ex.lockType)
        }

        val builder = ResponseEntity.status(ex.httpStatus)
        // Set Retry-After header for rate-limited responses (RFC 6585)
        if (ex is MfaAccountLockedException) {
            builder.header("Retry-After", ex.retryAfterSeconds.toString())
        }
        return builder.body(problem)
    }
}
