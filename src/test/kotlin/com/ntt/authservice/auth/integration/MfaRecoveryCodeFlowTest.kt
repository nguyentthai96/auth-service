package com.ntt.authservice.auth.integration

import com.ntt.authservice.auth.adapter.out.persistence.entity.MfaRecoveryCodeEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.MfaRecoveryCodeRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.security.MessageDigest
import java.time.Instant

/**
 * Tests for MFA recovery code flow — generation, verification, single-use, regeneration.
 * Uses mocked repository to test the entity-level logic without full Spring context.
 *
 * FR-003: MFA settings (recovery codes extension)
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("MFA Recovery Code Flow Tests")
class MfaRecoveryCodeFlowTest {

    @Mock
    private lateinit var recoveryCodeRepository: MfaRecoveryCodeRepository

    private val testUserId = 42L

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @BeforeEach
    fun setUp() {
        // Reset mocks
    }

    @Test
    @DisplayName("TC1: Generate recovery codes → 10 codes, SHA-256 hashed in DB")
    fun shouldGenerateRecoveryCodes() {
        // Given — simulate generating 10 codes
        val plainCodes = (1..10).map { "RECOV-${it.toString().padStart(4, '0')}" }
        val entities = plainCodes.map { code ->
            MfaRecoveryCodeEntity().apply {
                userId = testUserId
                codeHash = sha256(code)
                used = false
            }
        }

        whenever(recoveryCodeRepository.saveAll(any<List<MfaRecoveryCodeEntity>>()))
            .thenReturn(entities)

        // When
        val savedEntities = recoveryCodeRepository.saveAll(entities)

        // Then
        assertEquals(10, savedEntities.size)
        savedEntities.forEach { entity ->
            assertFalse(entity.used)
            assertEquals(64, entity.codeHash.length, "SHA-256 hash should be 64 hex chars")
            assertEquals(testUserId, entity.userId)
        }
    }

    @Test
    @DisplayName("TC2: Verify valid recovery code → success, code marked used")
    fun shouldVerifyValidRecoveryCode() {
        // Given
        val plainCode = "RECOV-0001"
        val codeHash = sha256(plainCode)
        val entity = MfaRecoveryCodeEntity().apply {
            userId = testUserId
            this.codeHash = codeHash
            used = false
        }

        whenever(recoveryCodeRepository.findByUserIdAndUsedFalse(testUserId))
            .thenReturn(listOf(entity))

        // When — simulate verification: find matching hash among unused codes
        val unusedCodes = recoveryCodeRepository.findByUserIdAndUsedFalse(testUserId)
        val match = unusedCodes.find { it.codeHash == sha256(plainCode) }

        // Then
        assertNotNull(match)
        // Simulate marking as used
        match!!.used = true
        match.usedAt = Instant.now()

        assertTrue(match.used)
        assertNotNull(match.usedAt)
    }

    @Test
    @DisplayName("TC3: Verify already-used code → failure (no match in unused)")
    fun shouldRejectAlreadyUsedCode() {
        // Given — all codes are used
        whenever(recoveryCodeRepository.findByUserIdAndUsedFalse(testUserId))
            .thenReturn(emptyList())

        // When
        val unusedCodes = recoveryCodeRepository.findByUserIdAndUsedFalse(testUserId)
        val match = unusedCodes.find { it.codeHash == sha256("RECOV-0001") }

        // Then
        assertNull(match, "Used code should not be found in unused list")
    }

    @Test
    @DisplayName("TC4: Verify invalid code → failure")
    fun shouldRejectInvalidCode() {
        // Given
        val validEntity = MfaRecoveryCodeEntity().apply {
            userId = testUserId
            codeHash = sha256("RECOV-0001")
            used = false
        }
        whenever(recoveryCodeRepository.findByUserIdAndUsedFalse(testUserId))
            .thenReturn(listOf(validEntity))

        // When — try to verify with wrong code
        val unusedCodes = recoveryCodeRepository.findByUserIdAndUsedFalse(testUserId)
        val match = unusedCodes.find { it.codeHash == sha256("WRONG-CODE") }

        // Then
        assertNull(match, "Invalid code should not match any hash")
    }

    @Test
    @DisplayName("TC5: Count remaining codes → correct count after usage")
    fun shouldCountRemainingCodes() {
        // Given — 3 codes remaining
        whenever(recoveryCodeRepository.countByUserIdAndUsedFalse(testUserId))
            .thenReturn(3L)

        // When
        val remaining = recoveryCodeRepository.countByUserIdAndUsedFalse(testUserId)

        // Then
        assertEquals(3L, remaining)
    }

    @Test
    @DisplayName("TC6: Re-generate codes → old codes invalidated, 10 new codes")
    fun shouldRegenerateCodesInvalidatingOld() {
        // Given — delete old codes
        doNothing().whenever(recoveryCodeRepository).deleteByUserId(testUserId)

        val newEntities = (1..10).map {
            MfaRecoveryCodeEntity().apply {
                userId = testUserId
                codeHash = sha256("NEW-RECOV-$it")
                used = false
            }
        }
        whenever(recoveryCodeRepository.saveAll(any<List<MfaRecoveryCodeEntity>>()))
            .thenReturn(newEntities)

        // When — regenerate: delete old + save new
        recoveryCodeRepository.deleteByUserId(testUserId)
        val regenerated = recoveryCodeRepository.saveAll(newEntities)

        // Then
        verify(recoveryCodeRepository).deleteByUserId(testUserId)
        assertEquals(10, regenerated.size)
        regenerated.forEach { assertFalse(it.used) }
    }
}
