package com.ntt.accountservice.profile.adapter.`in`.web

import com.ntt.accountservice.profile.adapter.`in`.web.dto.ProfileResponse
import com.ntt.accountservice.profile.adapter.`in`.web.dto.UpdateProfileRequest
import com.ntt.accountservice.profile.application.ProfileService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Profile controller — REST endpoints for user profile management (FR-005).
 */
@RestController
@RequestMapping("/api/account/profile")
class ProfileController(
    private val profileService: ProfileService
) {

    @GetMapping
    fun getProfile(): ResponseEntity<ProfileResponse> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(profileService.getProfile(userId))
    }

    @PutMapping
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ResponseEntity<ProfileResponse> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(profileService.updateProfile(userId, request))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw IllegalStateException("User not authenticated")
    }
}
