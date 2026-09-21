package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.`in`.web.dto.*
import com.ntt.authservice.auth.application.SsoAdapter
import com.ntt.authservice.auth.application.query.BuildAuthResponseHandler
import com.ntt.authservice.auth.application.query.BuildAuthResponseQuery
import com.ntt.authservice.shared.web.AuthenticatedController
import com.ntt.basecore.domain.web.payload.ApiResponse
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
    fun ssoCallback(@Valid @RequestBody request: SsoCallbackRequest): ResponseEntity<ApiResponse<Any>> {
        val response = ssoAdapter.handleCallback(
            code = request.code,
            provider = request.provider,
            redirectUri = request.redirectUri,
            authResponseBuilder = { userId ->
                AuthResponse.from(buildAuthResponseHandler.handle(BuildAuthResponseQuery(userId)))
            }
        )
        return okResponse(response)
    }

    @GetMapping("/providers")
    fun getProviders(@RequestParam(required = false) domainCode: String?): ResponseEntity<ApiResponse<List<SsoProviderInfoDto>>> {
        val providers = ssoAdapter.getProviders().map {
            SsoProviderInfoDto(id = it.id, name = it.name, enabled = it.enabled)
        }
        return okResponse(providers)
    }

    @PostMapping("/link")
    fun linkIdentity(@Valid @RequestBody request: SsoLinkRequest): ResponseEntity<ApiResponse<SsoLinkResult>> {
        ssoAdapter.linkIdentity(currentUserId(), request.code, request.provider, request.redirectUri)
        return okResponse(SsoLinkResult(linked = true), "auth.sso_identity_linked")
    }

    @DeleteMapping("/unlink/{provider}")
    fun unlinkIdentity(@PathVariable provider: String): ResponseEntity<ApiResponse<SsoLinkResult>> {
        ssoAdapter.unlinkIdentity(currentUserId(), provider)
        return okResponse(SsoLinkResult(linked = false), "auth.sso_identity_unlinked")
    }
}

