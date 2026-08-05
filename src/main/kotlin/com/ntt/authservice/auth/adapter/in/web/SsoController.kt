package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.AuthService
import com.ntt.authservice.auth.application.SsoAdapter
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * SSO endpoints — OAuth2 callback, provider listing, identity link/unlink.
 */
@RestController
@RequestMapping("/api/auth/sso")
class SsoController(
    private val ssoAdapter: SsoAdapter,
    private val authService: AuthService
) {

    @PostMapping("/callback")
    fun ssoCallback(@Valid @RequestBody request: SsoCallbackRequest): ResponseEntity<Any> {
        val response = ssoAdapter.handleCallback(
            code = request.code,
            provider = request.provider,
            redirectUri = request.redirectUri,
            authResponseBuilder = { userId -> authService.buildAuthResponseForUser(userId) }
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
    fun linkIdentity(@Valid @RequestBody request: SsoLinkRequest): ResponseEntity<Map<String, Boolean>> {
        val userId = getCurrentUserId()
        ssoAdapter.linkIdentity(userId, request.code, request.provider, request.redirectUri)
        return ResponseEntity.ok(mapOf("linked" to true))
    }

    @DeleteMapping("/unlink/{provider}")
    fun unlinkIdentity(@PathVariable provider: String): ResponseEntity<Map<String, Boolean>> {
        val userId = getCurrentUserId()
        ssoAdapter.unlinkIdentity(userId, provider)
        return ResponseEntity.ok(mapOf("linked" to false))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw com.ntt.authservice.shared.exception.InvalidCredentialsException()
    }
}
