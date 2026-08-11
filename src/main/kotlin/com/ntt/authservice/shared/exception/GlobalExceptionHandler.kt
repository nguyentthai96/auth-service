package com.ntt.authservice.shared.exception

import com.ntt.basecore.domain.web.BaseControllerAdvice
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletResponse
import java.net.URI

/**
 * Auth-service controller advice — extends BaseControllerAdvice (base-core)
 * and adds AuthException handling with RFC 7807 ProblemDetail + proper HTTP status.
 *
 * Bridge pattern: AuthException → BusinessException (base-core) for cross-service consistency,
 * but auth-specific errors get ProblemDetail with AuthErrorCode and correct HTTP status
 * instead of base-core's default 422 UNPROCESSABLE_ENTITY.
 *
 * I18n: Resolves error messages via MessageSource using AuthErrorCode.msgCode as key.
 * Content-Language header is set on all responses.
 *
 * Priority: Spring resolves handlers from most specific to least specific:
 * 1. AuthException → handleAuthException() (this class — returns ProblemDetail with correct status)
 * 2. BusinessException → handleBusinessException() (BaseControllerAdvice — returns ApiResponse with 422)
 * 3. Throwable → handleException() (BaseControllerAdvice — fallback)
 */
@RestControllerAdvice
class AuthControllerAdvice(
    validator: LocalValidatorFactoryBean,
    private val messageSource: MessageSource
) : BaseControllerAdvice(validator, messageSource) {

    /**
     * Handle AuthException hierarchy with RFC 7807 ProblemDetail.
     * Overrides base-core's BusinessException handler for auth-specific exceptions
     * to return the correct HTTP status per exception type instead of 422.
     *
     * Message resolution: messageSource.getMessage(authError.msgCode, args, locale)
     * where args are extracted from specific exception types (e.g., maxSessions, retryAfterSeconds).
     */
    @ExceptionHandler(AuthException::class)
    fun handleAuthException(
        ex: AuthException,
        response: HttpServletResponse
    ): ResponseEntity<ProblemDetail> {
        // Extract args for message interpolation based on exception type
        val args = extractMessageArgs(ex)

        // Resolve i18n message using AuthErrorCode.msgCode as message key
        val detail = resolveMessage(
            ex.authError.toErrorCodeBase().getMsgCode(),
            args,
            ex.message ?: ex.authError.toErrorCodeBase().getDesc() ?: "Authentication error"
        )

        val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, detail)
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
        if (ex is RateLimitExceededException) {
            problem.setProperty("retryAfterSeconds", ex.retryAfterSeconds)
            problem.setProperty("dimension", ex.dimension)
        }
        if (ex is SessionLimitExceededException) {
            problem.setProperty("maxSessions", ex.maxSessions)
            problem.setProperty("activeCount", ex.activeCount)
        }

        // Set Content-Language header
        setContentLanguageHeader(response)

        val builder = ResponseEntity.status(ex.httpStatus)
        // Set Retry-After header for rate-limited responses (RFC 6585)
        if (ex is MfaAccountLockedException) {
            builder.header("Retry-After", ex.retryAfterSeconds.toString())
        }
        if (ex is RateLimitExceededException) {
            builder.header("Retry-After", ex.retryAfterSeconds.toString())
        }
        return builder.body(problem)
    }

    /**
     * Extract interpolation arguments from specific exception types.
     * Maps exception properties to MessageFormat {0}, {1} placeholders.
     */
    private fun extractMessageArgs(ex: AuthException): Array<Any>? {
        return when (ex) {
            is RateLimitExceededException -> arrayOf(ex.retryAfterSeconds, ex.dimension)
            is SessionLimitExceededException -> arrayOf(ex.maxSessions)
            is MfaAccountLockedException -> arrayOf(ex.retryAfterSeconds)
            else -> null
        }
    }
}
