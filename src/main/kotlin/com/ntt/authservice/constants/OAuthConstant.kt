package com.ntt.authservice.constants


interface OAuthConstant {
    companion object {
        const val AUTHORIZATION_CODE: String = "authorization_code"
        const val REFRESH_TOKEN: String = "refresh_token"
        const val OAUTH_CODE_PRE: String = "OAUTH_CODE:"
        const val OAUTH_TOKEN_PRE: String = "OAUTH_TOKEN:"
        const val OAUTH_TOKEN_INFO_PRE: String = "OAUTH_TOKEN_INFO:"
        const val OAUTH_REFRESH_TOKEN_PRE: String = "OAUTH_REFRESH_TOKEN:"
    }
}
