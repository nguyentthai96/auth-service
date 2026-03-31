package com.ntt.authservice.controller.auth.payload

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 **/
data class LoginRequest(val username: String, val password: String)
data class RefreshTokenRequest(val refreshToken: String)
data class LogoutRequest(val accessToken: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, val newPassword: String)