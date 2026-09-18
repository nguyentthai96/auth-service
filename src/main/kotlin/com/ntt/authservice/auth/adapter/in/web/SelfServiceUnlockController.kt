package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.command.SelfServiceUnlockCommand
import com.ntt.authservice.auth.application.command.SelfServiceUnlockHandler
import com.ntt.authservice.auth.application.command.SelfServiceUnlockResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Self-service account unlock SSR controller (FR-013).
 * Renders Thymeleaf pages for the unlock email link flow:
 *   1. User clicks link → GET /unlock/confirm?token=xxx → confirmation page
 *   2. User clicks "Confirm Unlock" → GET /unlock/process?token=xxx → processes unlock
 *   3. Result → success.html or error.html
 *
 * Public endpoint (no authentication required — user is locked out).
 */
@Controller
@RequestMapping("/unlock")
class SelfServiceUnlockController(
    private val selfServiceUnlockHandler: SelfServiceUnlockHandler
) {

    private val log = LoggerFactory.getLogger(SelfServiceUnlockController::class.java)

    /**
     * Step 1: Show confirmation page before unlocking.
     * Security: Does NOT auto-unlock — requires explicit user action.
     */
    @GetMapping("/confirm")
    fun showConfirmPage(@RequestParam token: String, model: Model): String {
        model.addAttribute("token", token)
        return "unlock-confirm"
    }

    /**
     * Step 2: Process the unlock request.
     */
    @GetMapping("/process")
    fun processUnlock(@RequestParam token: String, model: Model): String {
        return try {
            val result = selfServiceUnlockHandler.handle(SelfServiceUnlockCommand(token))
            when (result) {
                is SelfServiceUnlockResult.Success -> {
                    log.info("SELF_UNLOCK_PAGE_SUCCESS userId={}", result.userId)
                    "unlock-success"
                }
                is SelfServiceUnlockResult.TokenExpired -> {
                    model.addAttribute("errorMessage", "Liên kết mở khóa đã hết hạn. Vui lòng yêu cầu liên kết mới.")
                    "unlock-error"
                }
                is SelfServiceUnlockResult.TokenUsedOrInvalid -> {
                    model.addAttribute("errorMessage", "Liên kết mở khóa không hợp lệ hoặc đã được sử dụng. Vui lòng yêu cầu liên kết mới.")
                    "unlock-error"
                }
                is SelfServiceUnlockResult.NotAllowed -> {
                    model.addAttribute("errorMessage", "Tài khoản bị khóa vĩnh viễn. Vui lòng liên hệ quản trị viên để mở khóa.")
                    "unlock-error"
                }
            }
        } catch (e: Exception) {
            log.error("SELF_UNLOCK_PAGE_ERROR: {}", e.message)
            model.addAttribute("errorMessage", "Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.")
            "unlock-error"
        }
    }
}
