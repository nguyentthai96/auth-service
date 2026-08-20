package com.ntt.accountservice.shared.exception

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
 * Account-service controller advice — extends BaseControllerAdvice (base-core).
 * Handles AccountException with RFC 7807 ProblemDetail + proper HTTP status.
 */
@RestControllerAdvice
class AccountControllerAdvice(
    validator: LocalValidatorFactoryBean,
    private val messageSource: MessageSource
) : BaseControllerAdvice(validator) {

    @ExceptionHandler(AccountException::class)
    fun handleAccountException(
        ex: AccountException,
        response: HttpServletResponse
    ): ResponseEntity<ProblemDetail> {
        val detail = resolveMessage(
            ex.accountError.toErrorCodeBase().getMsgCode(),
            null,
            ex.message ?: "Account service error"
        )

        val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, detail)
        problem.title = ex.accountError.getErrorCode()
        problem.type = URI.create("https://account-service/errors/${ex.accountError.name.lowercase()}")
        problem.setProperty("errorCode", ex.accountError.getErrorCode())

        response.setHeader("Content-Language", LocaleContextHolder.getLocale().toLanguageTag())

        return ResponseEntity.status(ex.httpStatus).body(problem)
    }

    private fun resolveMessage(msgCode: String?, args: Array<Any>?, defaultMessage: String): String {
        if (msgCode.isNullOrBlank()) return defaultMessage
        return messageSource.getMessage(msgCode, args, defaultMessage, LocaleContextHolder.getLocale())
            ?: defaultMessage
    }
}
