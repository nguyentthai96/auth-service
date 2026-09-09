package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.SsoAdapter
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * SSO endpoints — OAuth2 callback, provider listing, identity link/unlink.
 */
@RestController
@RequestMapping("/auth/sso")
class SsoController(
    private val ssoAdapter: SsoAdapter,
    private val buildAuthResponseHandler: BuildAuthResponseHandler,
    private val messageSource: MessageSource
) {

    @PostMapping("/callback")
    fun ssoCallback(@Valid @RequestBody request: SsoCallbackRequest): ResponseEntity<Any> {
        val response = ssoAdapter.handleCallback(
            code = request.code,
            provider = request.provider,
            redirectUri = request.redirectUri,
            authResponseBuilder = { userId ->
                AuthResponse.from(buildAuthResponseHandler.handle(BuildAuthResponseQuery(userId)))
            }
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/providers")
    fun getProviders(@RequestParam(required = false) domainCode: String?): ResponseEntity<List<SsoProviderInfoDto>> {
        val providers = ssoAdapter.getProviders().map {
            SsoProviderInfoDto(id = it.id, name = it.name, enabled = it.enabled)
        }
        return ResponseEntity.ok(providers)
    }

    @PostMapping("/link")
    fun linkIdentity(@Valid @RequestBody request: SsoLinkRequest): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        ssoAdapter.linkIdentity(userId, request.code, request.provider, request.redirectUri)
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.sso_identity_linked", null, "SSO identity linked successfully", locale)
        return ResponseEntity.ok(mapOf("linked" to true, "message" to message))
    }

    @DeleteMapping("/unlink/{provider}")
    fun unlinkIdentity(@PathVariable provider: String): ResponseEntity<Map<String, Any?>> {
        val userId = getCurrentUserId()
        ssoAdapter.unlinkIdentity(userId, provider)
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.sso_identity_unlinked", null, "SSO identity unlinked successfully", locale)
        return ResponseEntity.ok(mapOf("linked" to false, "message" to message))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw com.ntt.authservice.shared.exception.InvalidCredentialsException()
    }
}
