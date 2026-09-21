package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.command.UnlockUserCommand
import com.ntt.authservice.auth.application.command.UnlockUserHandler
import com.ntt.authservice.shared.web.AdminController
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Admin endpoint to unlock a locked user account (FR-009).
 * Requires ROLE_ADMIN or SUPER_ADMIN authority.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUnlockController(
    private val unlockUserHandler: UnlockUserHandler
) : AdminController() {

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    fun unlockUser(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
        val adminId = requestContext.userId
            ?: throw IllegalStateException("Cannot resolve admin user ID")

        unlockUserHandler.handle(UnlockUserCommand(userId = userId, adminId = adminId))

        return ResponseEntity.ok(
            mapOf(
                "status" to "unlocked",
                "userId" to userId,
                "message" to "Account has been unlocked successfully"
            )
        )
    }
}
