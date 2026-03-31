package com.ntt.authservice.controller.auth

import com.ntt.authservice.controller.auth.payload.*
import com.ntt.basecore.domain.web.BaseController
import org.springframework.web.bind.annotation.*

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 **/
@RestController
@RequestMapping("/api/oauth")
class OAuthController(
        private val tokenStore: TokenStore
) : BaseController() {

    @DeleteMapping("/revoke-token")
    fun revokeToken(@RequestParam("token") token: String) {
        val accessToken = tokenStore.readAccessToken(token)
        if (accessToken != null) {
            tokenStore.removeAccessToken(accessToken)
            val refreshToken = accessToken.refreshToken
            if (refreshToken != null) {
                tokenStore.removeRefreshToken(refreshToken)
            }
        }
    }
}