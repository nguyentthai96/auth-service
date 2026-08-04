package com.ntt.authservice.shared.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * Global exception handler returning RFC 7807 ProblemDetail.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, ex.message)
        problem.title = ex.errorCode
        problem.type = URI.create("https://auth-service/errors/${ex.errorCode.lowercase()}")
        problem.setProperty("errorCode", ex.errorCode)
        return problem
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
        problem.title = "RESOURCE_NOT_FOUND"
        return problem
    }

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Duplicate resource")
        problem.title = "DUPLICATE_RESOURCE"
        return problem
    }

    @ExceptionHandler(PolicyEvaluationException::class)
    fun handlePolicyEvaluation(ex: PolicyEvaluationException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Policy evaluation failed")
        problem.title = "POLICY_EVALUATION_ERROR"
        return problem
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        )
        problem.title = "VALIDATION_ERROR"
        problem.setProperty("violations", ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to (it.defaultMessage ?: "Invalid value"))
        })
        return problem
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException::class)
    fun handleValidation(ex: jakarta.validation.ConstraintViolationException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        )
        problem.title = "VALIDATION_ERROR"
        problem.setProperty("violations", ex.constraintViolations.map {
            mapOf("field" to it.propertyPath.toString(), "message" to it.message)
        })
        return problem
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        )
        problem.title = "INTERNAL_ERROR"
        // Never leak stack traces to API consumers
        return problem
    }
}

