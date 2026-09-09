package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.LockInfo
import com.ntt.authservice.auth.application.MfaRateLimitService
import com.ntt.authservice.auth.application.RateLimitType
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Admin API for managing MFA rate limit locks.
 *
 * Requires ADMIN role. All operations are audited (FR-008).
 */
@RestController
@RequestMapping("/admin/rate-limits")
@PreAuthorize("hasRole('ADMIN')")
class RateLimitAdminController(
    private val rateLimitService: MfaRateLimitService,
    private val messageSource: MessageSource
) {

    /**
     * Get lock status for a user across all MFA rate limit types.
     *
     * @param userId target user ID
     * @return map of lock type → lock info (null if not locked)
     */
    @GetMapping("/locks/{userId}")
    fun getLockInfo(@PathVariable userId: Long): ResponseEntity<LockInfoResponse> {
        val lockInfo = rateLimitService.getLockInfo(userId)
        return ResponseEntity.ok(
            LockInfoResponse(
                userId = userId,
                locks = lockInfo.mapKeys { it.key.key }.filterValues { it != null }
                    .mapValues { (_, info) ->
                        LockDetail(
                            retryAfterSeconds = info!!.retryAfterSeconds,
                            lockedAt = info.lockedAt.toString()
                        )
                    }
            )
        )
    }

    /**
     * Admin manual unlock — clears all MFA rate limit locks for user.
     *
     * @param userId target user ID
     * @return confirmation with i18n message
     */
    @DeleteMapping("/locks/{userId}")
    fun adminUnlock(@PathVariable userId: Long): ResponseEntity<Map<String, Any?>> {
        rateLimitService.adminUnlock(userId)
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.rate_limit_unlocked", null, "Rate limit locks cleared successfully", locale)
        return ResponseEntity.ok(mapOf("unlocked" to true, "userId" to userId, "message" to message))
    }

    data class LockInfoResponse(
        val userId: Long,
        val locks: Map<String, LockDetail>
    )

    data class LockDetail(
        val retryAfterSeconds: Long,
        val lockedAt: String
    )
}
