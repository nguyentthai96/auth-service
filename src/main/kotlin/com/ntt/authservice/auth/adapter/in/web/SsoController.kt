package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.SsoAdapter
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.shared.web.AuthenticatedController
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * SSO endpoints — OAuth2 callback, provider listing, identity link/unlink.
 */
@RestController
@RequestMapping("/auth/sso")
class SsoController(
    private val ssoAdapter: SsoAdapter,
    private val buildAuthResponseHandler: BuildAuthResponseHandler
) : AuthenticatedController() {

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
        ssoAdapter.linkIdentity(currentUserId(), request.code, request.provider, request.redirectUri)
        return ResponseEntity.ok(mapOf("linked" to true, "message" to message("auth.sso_identity_linked")))
    }

    @DeleteMapping("/unlink/{provider}")
    fun unlinkIdentity(@PathVariable provider: String): ResponseEntity<Map<String, Any?>> {
        ssoAdapter.unlinkIdentity(currentUserId(), provider)
        return ResponseEntity.ok(mapOf("linked" to false, "message" to message("auth.sso_identity_unlinked")))
    }
}
