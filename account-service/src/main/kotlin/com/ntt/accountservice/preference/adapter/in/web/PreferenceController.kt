package com.ntt.accountservice.preference.adapter.`in`.web

import com.ntt.accountservice.preference.application.PreferenceService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Preference controller — REST endpoints for user preferences (FR-006).
 */
@RestController
@RequestMapping("/api/account/preferences")
class PreferenceController(
    private val preferenceService: PreferenceService
) {

    @GetMapping
    fun getAllPreferences(): ResponseEntity<Map<String, Map<String, String>>> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(preferenceService.getAllPreferences(userId))
    }

    @GetMapping("/{category}")
    fun getPreferencesByCategory(@PathVariable category: String): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(preferenceService.getPreferencesByCategory(userId, category))
    }

    @PatchMapping("/{category}")
    fun updatePreferences(
        @PathVariable category: String,
        @RequestBody preferences: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val userId = getCurrentUserId()
        return ResponseEntity.ok(preferenceService.updatePreferences(userId, category, preferences))
    }

    private fun getCurrentUserId(): Long {
        return (SecurityContextHolder.getContext().authentication?.principal as? String)?.toLong()
            ?: throw IllegalStateException("User not authenticated")
    }
}
