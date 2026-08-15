package com.ntt.authservice.auth.adapter.out.cipher

import com.ntt.basecore.autoconfigure.security.cipher.core.CipherAlgorithm
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for TinkCipherAlgorithmFactory.
 * Tests: AES-GCM roundtrip, ChaCha20 roundtrip, AAD mismatch, wrong key.
 */
class TinkCipherAlgorithmFactoryTest {

    private val factory = TinkCipherAlgorithmFactory()

    @Test
    fun `AES-GCM encrypt then decrypt should return original plaintext`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Hello, E2EE!".toByteArray()
        val aad = "context:user123".toByteArray()

        val ciphertext = factory.encrypt(CipherAlgorithm.AES_GCM, key, plaintext, aad)
        assertNotNull(ciphertext)
        assert(ciphertext.size > plaintext.size) { "Ciphertext should be larger than plaintext" }

        val decrypted = factory.decrypt(CipherAlgorithm.AES_GCM, key, ciphertext, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `ChaCha20-Poly1305 encrypt then decrypt should return original plaintext`() {
        val key = ByteArray(32) { (it + 10).toByte() }
        val plaintext = "ChaCha20 test data".toByteArray()
        val aad = "context:device456".toByteArray()

        val ciphertext = factory.encrypt(CipherAlgorithm.CHACHA20_POLY1305, key, plaintext, aad)
        assertNotNull(ciphertext)

        val decrypted = factory.decrypt(CipherAlgorithm.CHACHA20_POLY1305, key, ciphertext, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong AAD should fail`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "AAD mismatch test".toByteArray()
        val correctAad = "correct-context".toByteArray()
        val wrongAad = "wrong-context".toByteArray()

        val ciphertext = factory.encrypt(CipherAlgorithm.AES_GCM, key, plaintext, correctAad)

        assertThrows<Exception> {
            factory.decrypt(CipherAlgorithm.AES_GCM, key, ciphertext, wrongAad)
        }
    }

    @Test
    fun `decrypt with different key should fail`() {
        val key1 = ByteArray(32) { it.toByte() }
        val key2 = ByteArray(32) { (it + 1).toByte() }
        val plaintext = "Key mismatch test".toByteArray()
        val aad = ByteArray(0)

        val ciphertext = factory.encrypt(CipherAlgorithm.AES_GCM, key1, plaintext, aad)

        assertThrows<Exception> {
            factory.decrypt(CipherAlgorithm.AES_GCM, key2, ciphertext, aad)
        }
    }
}
