package com.ntt.authservice.auth.application

import io.jsonwebtoken.Claims
import io.jsonwebtoken.impl.DefaultClaims
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Unit tests for ClaimValidatorChain — chain of responsibility orchestrator.
 * Tests fail-fast (validateOrThrow) and collect-all (validateAll) modes.
 *
 * FR-004: Claim validation pipeline.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("ClaimValidatorChain Tests")
class ClaimValidatorChainTest {

    private val claims: Claims = DefaultClaims(mapOf("sub" to "42"))

    private fun passValidator(name: String): ClaimValidator {
        val v = mock<ClaimValidator>()
        whenever(v.validate(any())).thenReturn(
            ClaimValidationResult(name, ClaimValidationStatus.PASS)
        )
        return v
    }

    private fun failValidator(name: String, reason: String): ClaimValidator {
        val v = mock<ClaimValidator>()
        whenever(v.validate(any())).thenReturn(
            ClaimValidationResult(name, ClaimValidationStatus.FAIL, reason)
        )
        return v
    }

    @Test
    @DisplayName("TC01: validateOrThrow — all validators return PASS → no exception thrown")
    fun shouldNotThrowWhenAllValidatorsPass() {
        // Given
        val chain = ClaimValidatorChain(listOf(
            passValidator("V1"),
            passValidator("V2"),
            passValidator("V3")
        ))

        // When / Then
        assertDoesNotThrow { chain.validateOrThrow(claims) }
    }

    @Test
    @DisplayName("TC02: validateOrThrow — first validator FAIL → throws ClaimValidationException, remaining NOT called")
    fun shouldThrowOnFirstFailAndStopProcessing() {
        // Given
        val v1 = failValidator("FailingValidator", "some reason")
        val v2 = passValidator("NeverReached")
        val chain = ClaimValidatorChain(listOf(v1, v2))

        // When / Then
        val exception = assertThrows(ClaimValidationException::class.java) {
            chain.validateOrThrow(claims)
        }
        assertEquals("FailingValidator", exception.validatorName)
        assertTrue(exception.message!!.contains("some reason"))

        // Verify v2 was never called (fail-fast)
        verify(v2, never()).validate(any())
    }

    @Test
    @DisplayName("TC03: validateAll — all validators PASS → returns list of all PASS results")
    fun shouldReturnAllPassResults() {
        // Given
        val chain = ClaimValidatorChain(listOf(
            passValidator("V1"),
            passValidator("V2")
        ))

        // When
        val results = chain.validateAll(claims)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == ClaimValidationStatus.PASS })
    }

    @Test
    @DisplayName("TC04: validateAll — mixed PASS/FAIL → returns ALL results (doesn't short-circuit)")
    fun shouldReturnAllResultsIncludingFailures() {
        // Given
        val v1 = passValidator("V1")
        val v2 = failValidator("V2", "V2 failed")
        val v3 = passValidator("V3")
        val chain = ClaimValidatorChain(listOf(v1, v2, v3))

        // When
        val results = chain.validateAll(claims)

        // Then
        assertEquals(3, results.size)
        assertEquals(ClaimValidationStatus.PASS, results[0].status)
        assertEquals(ClaimValidationStatus.FAIL, results[1].status)
        assertEquals(ClaimValidationStatus.PASS, results[2].status)

        // All validators were called (collect-all, no short-circuit)
        verify(v1).validate(any())
        verify(v2).validate(any())
        verify(v3).validate(any())
    }

    @Test
    @DisplayName("TC05: Empty validators list → validateOrThrow no exception, validateAll returns empty list")
    fun shouldHandleEmptyValidatorsList() {
        // Given
        val chain = ClaimValidatorChain(emptyList())

        // When / Then
        assertDoesNotThrow { chain.validateOrThrow(claims) }
        assertEquals(emptyList<ClaimValidationResult>(), chain.validateAll(claims))
    }
}
