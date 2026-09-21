package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.AltchaChallenge
import com.ntt.authservice.auth.application.AltchaCaptchaVerifier
import com.ntt.authservice.auth.application.ImageCaptchaChallenge
import com.ntt.authservice.auth.application.ImageCaptchaStrategy
import com.ntt.authservice.shared.web.BaseController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * CAPTCHA challenge endpoint — generates challenges for client-side solving.
 * Supports dual CAPTCHA types: ALTCHA (PoW) and Image (Kaptcha).
 * Public endpoint (no authentication required).
 *
 * Usage:
 *   GET /captcha/challenge?type=altcha  → PoW challenge
 *   GET /captcha/challenge?type=image   → Base64 image challenge
 *   GET /captcha/challenge              → default type (from config)
 */
@RestController
@RequestMapping("/captcha")
class CaptchaController(
    private val altchaCaptchaVerifier: AltchaCaptchaVerifier,
    private val imageCaptchaStrategy: ImageCaptchaStrategy
) : BaseController() {

    @GetMapping("/challenge")
    fun getChallenge(
        @RequestParam(required = false, defaultValue = "altcha") type: String
    ): ResponseEntity<*> {
        return when (type.lowercase()) {
            "image" -> ResponseEntity.ok(imageCaptchaStrategy.generateChallenge())
            else -> ResponseEntity.ok(altchaCaptchaVerifier.generateChallenge())
        }
    }
}
