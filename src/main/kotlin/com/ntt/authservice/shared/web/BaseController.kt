package com.ntt.authservice.shared.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * Abstract base controller providing response helpers and i18n support.
 *
 * All controllers should extend this (or its subclasses) to eliminate duplicate
 * response formatting and message resolution boilerplate.
 *
 * Uses setter injection for [RequestContext] and [MessageSource] so that
 * subclasses do NOT need to forward these through their constructors.
 * Spring injects these automatically when creating the concrete controller bean.
 *
 * FR-004: BaseController with response helpers.
 */
abstract class BaseController {

    protected lateinit var requestContext: RequestContext
        private set

    protected lateinit var messageSource: MessageSource
        private set

    /**
     * Setter injection for base dependencies.
     * Called automatically by Spring when the concrete controller bean is created.
     * Subclasses do NOT need to declare requestContext/messageSource in their constructors.
     */
    @Autowired
    fun injectBaseDependencies(requestContext: RequestContext, messageSource: MessageSource) {
        this.requestContext = requestContext
        this.messageSource = messageSource
    }

    /**
     * Returns HTTP 200 with the given body.
     */
    protected fun <T : Any> ok(body: T): ResponseEntity<T> =
        ResponseEntity.ok(body)

    /**
     * Returns HTTP 200 with body and an i18n message.
     */
    protected fun ok(body: Any, messageKey: String, vararg args: Any?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(
            mapOf(
                "data" to body,
                "message" to message(messageKey, *args)
            )
        )

    /**
     * Returns HTTP 200 with only an i18n message.
     * Replaces the common pattern: ResponseEntity.ok(mapOf("message" to messageSource.getMessage(...)))
     */
    protected fun okMessage(messageKey: String, vararg args: Any?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("message" to message(messageKey, *args)))

    /**
     * Returns HTTP 201 CREATED with the given body.
     */
    protected fun <T : Any> created(body: T): ResponseEntity<T> =
        ResponseEntity.status(HttpStatus.CREATED).body(body)

    /**
     * Returns HTTP 204 NO CONTENT.
     */
    protected fun noContent(): ResponseEntity<Void> =
        ResponseEntity.noContent().build()

    /**
     * Resolves an i18n message using the request locale from [RequestContext].
     * Uses the message key as default message if no translation found.
     */
    protected fun message(key: String, vararg args: Any?): String {
        val argsArray: Array<Any>? = if (args.isEmpty()) null else args.filterNotNull().toTypedArray()
        return messageSource.getMessage(key, argsArray, key, requestContext.locale) ?: key
    }

    /**
     * Returns HTTP 200 with a paginated response containing page metadata.
     */
    protected fun <T : Any> pagedResponse(page: Page<T>): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(
            mapOf(
                "content" to page.content,
                "page" to page.number,
                "size" to page.size,
                "totalElements" to page.totalElements,
                "totalPages" to page.totalPages,
                "first" to page.isFirst,
                "last" to page.isLast
            )
        )
}
