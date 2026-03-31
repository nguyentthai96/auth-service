package com.ntt.authservice.controller.auth

import com.ntt.authservice.controller.auth.payload.*
import com.ntt.authservice.domain.auth.service.AuthService
import com.ntt.basecore.domain.web.BaseController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 **/
@RestController
@RequestMapping("/api/auth")
class AuthController(
        private val authService: AuthService
) : BaseController() {

    @PostMapping("/login")
    fun login (@RequestBody request: LoginRequest):ResponseEntity<AuthResponse> {
        val response = authService.login(request.username, request.password)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refreshToken (@RequestBody request: RefreshTokenRequest):ResponseEntity<AuthResponse> {
        val response = authService.refreshToken(request.refreshToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/register")
    fun register (@RequestBody user:User):ResponseEntity<User> {
        val registeredUser = authService.register(user)
        return ResponseEntity.ok(registeredUser)
    }

    @GetMapping("/verify-email")
    fun verifyEmail (@RequestParam token:String):ResponseEntity<Boolean> {
        val isVerified = authService.verifyEmail(token)
        return ResponseEntity.ok(isVerified)
    }

    @PostMapping("/logout")
    fun logout (@RequestBody request: LogoutRequest):ResponseEntity<Boolean> {
        val isLoggedOut = authService.logout(request.accessToken)
        return ResponseEntity.ok(isLoggedOut)
    }

    @PostMapping("/forgot-password")
    fun forgotPassword (@RequestBody request: ForgotPasswordRequest):ResponseEntity<Boolean> {
        val isEmailSent = authService.forgotPassword(request.email)
        return ResponseEntity.ok(isEmailSent)
    }

    @PostMapping("/reset-password")
    fun resetPassword (@RequestBody request: ResetPasswordRequest):ResponseEntity<Boolean> {
        val isPasswordReset = authService.resetPassword(request.token, request.newPassword)
        return ResponseEntity.ok(isPasswordReset)
    }
}