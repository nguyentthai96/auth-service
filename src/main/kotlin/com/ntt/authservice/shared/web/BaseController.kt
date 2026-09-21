package com.ntt.authservice.shared.web

import com.ntt.basecore.domain.web.payload.ApiResponse
import com.ntt.basecore.domain.web.payload.PageResponse
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
 * Response helpers come in two flavors:
 * - **Raw helpers** (`ok`, `created`): Return raw body — backward compatible.
 * - **ApiResponse helpers** (`okResponse`, `createdResponse`, `pagedOkResponse`):
 *   Return standardized `ApiResponse<T>` envelope from base-core.
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

    // ── ApiResponse<T> wrapped helpers (PREFERRED) ──────────────────────

    /**
     * Returns HTTP 200 with data wrapped in [ApiResponse].
     */
    protected fun <T> okResponse(data: T): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.ok(ApiResponse.success(data))

    /**
     * Returns HTTP 200 with data and i18n message wrapped in [ApiResponse].
     */
    protected fun <T> okResponse(data: T, messageKey: String, vararg args: Any?): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.ok(ApiResponse.success(data, message(messageKey, *args)))

    /**
     * Returns HTTP 200 with only an i18n message in [ApiResponse] (no data).
     * Replaces: `ResponseEntity.ok(mapOf("message" to message(...)))`
     */
    protected fun okMessageResponse(messageKey: String, vararg args: Any?): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.ok(ApiResponse.success(message(messageKey, *args)))

    /**
     * Returns HTTP 201 CREATED with data wrapped in [ApiResponse].
     */
    protected fun <T> createdResponse(data: T, messageKey: String? = null): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(data, messageKey?.let { message(it) } ?: ""))

    /**
     * Returns HTTP 200 with paginated data wrapped in [ApiResponse]<[PageResponse]<T>>.
     * Uses base-core [PageResponse.from] to extract page metadata.
     */
    protected fun <T : Any> pagedOkResponse(page: Page<T>): ResponseEntity<ApiResponse<PageResponse<T>>> =
        ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)))

    // ── Raw helpers (backward compatible) ────────────────────────────────

    /**
     * Returns HTTP 200 with the given body (raw, no envelope).
     */
    protected fun <T : Any> ok(body: T): ResponseEntity<T> =
        ResponseEntity.ok(body)

    /**
     * Returns HTTP 200 with body and an i18n message (legacy Map format).
     */
    @Deprecated("Use okResponse(data, messageKey) instead", ReplaceWith("okResponse(body, messageKey, *args)"))
    protected fun ok(body: Any, messageKey: String, vararg args: Any?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(
            mapOf(
                "data" to body,
                "message" to message(messageKey, *args)
            )
        )

    /**
     * Returns HTTP 200 with only an i18n message (legacy Map format).
     */
    @Deprecated("Use okMessageResponse(messageKey) instead", ReplaceWith("okMessageResponse(messageKey, *args)"))
    protected fun okMessage(messageKey: String, vararg args: Any?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("message" to message(messageKey, *args)))

    /**
     * Returns HTTP 201 CREATED with the given body (raw, no envelope).
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
     * Returns HTTP 200 with a paginated response (legacy Map format).
     */
    @Deprecated("Use pagedOkResponse(page) instead", ReplaceWith("pagedOkResponse(page)"))
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
