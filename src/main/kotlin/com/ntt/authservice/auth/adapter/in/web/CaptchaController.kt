package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.application.AltchaChallenge
import com.ntt.authservice.auth.application.AltchaCaptchaVerifier
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * CAPTCHA challenge endpoint — generates ALTCHA PoW challenges for client-side solving.
 * Public endpoint (no authentication required).
 */
@RestController
@RequestMapping("/api/captcha")
class CaptchaController(
    private val altchaCaptchaVerifier: AltchaCaptchaVerifier
) {

    @GetMapping("/challenge")
    fun getChallenge(): ResponseEntity<AltchaChallenge> {
        val challenge = altchaCaptchaVerifier.generateChallenge()
        return ResponseEntity.ok(challenge)
    }
}
