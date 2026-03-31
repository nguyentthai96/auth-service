package com.ntt.authservice.domain.auth.service

import com.ntt.authservice.controller.auth.payload.AuthResponse
import com.ntt.authservice.controller.auth.payload.User

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 **/
interface AuthService {
    fun login(username: String, password: String): AuthResponse
    fun refreshToken(refreshToken: String): AuthResponse
    fun register(user: User): User
    fun verifyEmail(token: String): Boolean
    fun logout(accessToken: String): Boolean
    fun forgotPassword(email: String): Boolean
    fun resetPassword(token: String, newPassword: String): Boolean
}