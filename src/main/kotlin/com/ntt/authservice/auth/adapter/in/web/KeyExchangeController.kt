package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeRequest
import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeResponse
import com.ntt.basecore.autoconfigure.security.cipher.key.KeyExchangeService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Key exchange endpoint for E2EE session negotiation.
 *
 * POST /auth/key-exchange — excluded from CipherFilter (plaintext endpoint).
 * Client sends X25519 public key, receives server public key + session metadata.
 */
@RestController
@RequestMapping("/auth/key-exchange")
class KeyExchangeController(
    private val keyExchangeService: KeyExchangeService
) {

    /**
     * Perform X25519 ECDH key exchange.
     *
     * @param request Client key exchange request with public key and device info
     * @return Server public key, key session ID, and expiry metadata
     */
    @PostMapping
    fun exchange(@Valid @RequestBody request: KeyExchangeRequest): ResponseEntity<KeyExchangeResponse> {
        val response = keyExchangeService.exchange(request)
        return ResponseEntity.ok(response)
    }
}
