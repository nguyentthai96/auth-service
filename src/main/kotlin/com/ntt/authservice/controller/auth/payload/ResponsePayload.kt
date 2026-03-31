package com.ntt.authservice.controller.auth.payload

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 **/
data class AuthResponse(val accessToken: String, val refreshToken: String, val expiresAt: String)