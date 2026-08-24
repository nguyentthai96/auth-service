package com.ntt.authservice.auth.application.event

import com.ntt.authservice.auth.domain.event.IssuanceContext
import com.ntt.authservice.auth.domain.event.RevocationType
import com.ntt.authservice.auth.domain.event.TokenIssuedEvent
import com.ntt.authservice.auth.domain.event.TokenRevokedEvent
import com.ntt.authservice.auth.domain.event.TokenValidationFailedEvent
import com.ntt.authservice.auth.domain.event.ValidationFailureReason
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Unit tests for TokenEventRecorder — token lifecycle event recording helper.
 * Verifies delegation to EventService.record() with correct parameters.
 *
 * FR-009: Token issuance events, FR-012: Token revocation events + validation failure events
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("TokenEventRecorder Tests")
class TokenEventRecorderTest {

    @Mock private lateinit var eventService: EventService

    private lateinit var recorder: TokenEventRecorder

    @BeforeEach
    fun setUp() {
        recorder = TokenEventRecorder(eventService)
    }

    // --- Issuance Tests ---

    private fun createTokenIssuedEvent(context: IssuanceContext) = TokenIssuedEvent(
        userId = 100L,
        username = "testuser",
        domainCode = "default",
        domainId = 1L,
        issuanceContext = context,
        accessTokenJti = "jti-abc-123",
        refreshTokenHash = "sha256-refresh-hash",
        roles = listOf("USER"),
        permissions = listOf("read"),
        accessTokenExpiresAt = Instant.now().plusSeconds(900),
        refreshTokenExpiresAt = Instant.now().plusSeconds(604800)
    )

    @Test
    @DisplayName("TC1: recordIssuance() creates event with correct jti, userId")
    fun shouldRecordIssuanceWithCorrectFields() {
        // Given
        val event = createTokenIssuedEvent(IssuanceContext.LOGIN)

        // When
        recorder.recordIssuance(event, userId = 100L, correlationId = "corr-1")

        // Then
        verify(eventService).record(
            aggregateType = eq("User"),
            aggregateId = eq(100L),
            event = eq(event),
            topic = eq("iam.token.issued"),
            partitionKey = eq("100"),
            correlationId = eq("corr-1")
        )
    }

    @Test
    @DisplayName("TC2: recordIssuance() with IssuanceContext.LOGIN → correct event type")
    fun shouldRecordLoginIssuance() {
        val event = createTokenIssuedEvent(IssuanceContext.LOGIN)
        recorder.recordIssuance(event, 100L, null)

        verify(eventService).record(
            any(), any(),
            argThat<TokenIssuedEvent> { it.issuanceContext == IssuanceContext.LOGIN },
            any(), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC3: recordIssuance() with IssuanceContext.TOKEN_REFRESH → correct event type")
    fun shouldRecordRefreshIssuance() {
        val event = createTokenIssuedEvent(IssuanceContext.TOKEN_REFRESH)
        recorder.recordIssuance(event, 100L, null)

        verify(eventService).record(
            any(), any(),
            argThat<TokenIssuedEvent> { it.issuanceContext == IssuanceContext.TOKEN_REFRESH },
            any(), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC4: recordIssuance() with IssuanceContext.SSO → correct event type")
    fun shouldRecordSsoIssuance() {
        val event = createTokenIssuedEvent(IssuanceContext.SSO)
        recorder.recordIssuance(event, 100L, null)

        verify(eventService).record(
            any(), any(),
            argThat<TokenIssuedEvent> { it.issuanceContext == IssuanceContext.SSO },
            any(), any(), anyOrNull()
        )
    }

    // --- Revocation Tests ---

    @Test
    @DisplayName("TC5: recordRevocation() creates TokenRevokedEvent with correct jti")
    fun shouldRecordRevocationWithCorrectFields() {
        val event = TokenRevokedEvent(
            userId = 200L,
            revocationType = RevocationType.LOGOUT,
            revokedTokenHash = "revoked-hash",
            revokedAccessTokenJti = "jti-revoked-1"
        )
        recorder.recordRevocation(event, 200L, "corr-2")

        verify(eventService).record(
            aggregateType = eq("User"),
            aggregateId = eq(200L),
            event = eq(event),
            topic = eq("iam.token.revoked"),
            partitionKey = eq("200"),
            correlationId = eq("corr-2")
        )
    }

    @Test
    @DisplayName("TC6: recordRevocation() with RevocationType.LOGOUT → correct event")
    fun shouldRecordLogoutRevocation() {
        val event = TokenRevokedEvent(userId = 200L, revocationType = RevocationType.LOGOUT)
        recorder.recordRevocation(event, 200L, null)

        verify(eventService).record(
            any(), any(),
            argThat<TokenRevokedEvent> { it.revocationType == RevocationType.LOGOUT },
            any(), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC7: recordRevocation() with RevocationType.BULK_REVOKE → correct event")
    fun shouldRecordBulkRevocation() {
        val event = TokenRevokedEvent(
            userId = 200L, revocationType = RevocationType.BULK_REVOKE,
            revokedCount = 5, reason = "Admin force logout"
        )
        recorder.recordRevocation(event, 200L, null)

        verify(eventService).record(
            any(), any(),
            argThat<TokenRevokedEvent> {
                it.revocationType == RevocationType.BULK_REVOKE && it.revokedCount == 5
            },
            any(), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC8: recordRevocation() with RevocationType.ROTATION → correct event")
    fun shouldRecordRotationRevocation() {
        val event = TokenRevokedEvent(userId = 200L, revocationType = RevocationType.ROTATION)
        recorder.recordRevocation(event, 200L, null)

        verify(eventService).record(
            any(), any(),
            argThat<TokenRevokedEvent> { it.revocationType == RevocationType.ROTATION },
            any(), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC9: Both methods delegate to eventService.record()")
    fun shouldDelegateToEventService() {
        val issuanceEvent = createTokenIssuedEvent(IssuanceContext.LOGIN)
        val revocationEvent = TokenRevokedEvent(userId = 100L, revocationType = RevocationType.LOGOUT)

        recorder.recordIssuance(issuanceEvent, 100L, null)
        recorder.recordRevocation(revocationEvent, 100L, null)

        verify(eventService, times(2)).record(
            any(), any(), any(), any(), any(), anyOrNull()
        )
    }

    @Test
    @DisplayName("TC-extra: recordIssuance() swallows EventService exceptions (fail-safe)")
    fun shouldSwallowExceptionsOnIssuance() {
        // Given — EventService throws
        whenever(eventService.record<TokenIssuedEvent>(any(), any(), any(), any(), any(), anyOrNull()))
            .thenThrow(RuntimeException("Event store down"))

        val event = createTokenIssuedEvent(IssuanceContext.LOGIN)

        // When — should NOT throw
        recorder.recordIssuance(event, 100L, null)

        // Then — logged but not propagated (verified by no exception thrown)
    }

    @Test
    @DisplayName("TC-extra: recordRevocation() swallows EventService exceptions (fail-safe)")
    fun shouldSwallowExceptionsOnRevocation() {
        // Given
        whenever(eventService.record<TokenRevokedEvent>(any(), any(), any(), any(), any(), anyOrNull()))
            .thenThrow(RuntimeException("Event store down"))

        val event = TokenRevokedEvent(userId = 200L, revocationType = RevocationType.LOGOUT)

        // When — should NOT throw
        recorder.recordRevocation(event, 200L, null)

        // Then — logged but not propagated
    }

    // --- Validation Failure Tests (new TCs — FR-012) ---

    @Test
    @DisplayName("TC10: recordValidationFailure() delegates to eventService.record() with correct topic and aggregateType")
    fun shouldRecordValidationFailureWithCorrectParameters() {
        // Given
        val event = TokenValidationFailedEvent(
            reason = ValidationFailureReason.ISSUER_MISMATCH,
            tokenJti = "jti-val-123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            validatorName = "IssuerClaimValidator"
        )

        // When
        recorder.recordValidationFailure(event, correlationId = "corr-val-1")

        // Then
        verify(eventService).record(
            aggregateType = eq("Token"),
            aggregateId = eq(0L),
            event = eq(event),
            topic = eq("iam.token.validation-failed"),
            partitionKey = eq("jti-val-123"),
            correlationId = eq("corr-val-1")
        )
    }

    @Test
    @DisplayName("TC11: recordValidationFailure() with null tokenJti → partitionKey = \"unknown\"")
    fun shouldUseUnknownPartitionKeyWhenJtiIsNull() {
        // Given
        val event = TokenValidationFailedEvent(
            reason = ValidationFailureReason.SIGNATURE_INVALID,
            tokenJti = null,
            ipAddress = "10.0.0.1",
            userAgent = null,
            validatorName = null
        )

        // When
        recorder.recordValidationFailure(event, correlationId = null)

        // Then
        verify(eventService).record(
            aggregateType = eq("Token"),
            aggregateId = eq(0L),
            event = eq(event),
            topic = eq("iam.token.validation-failed"),
            partitionKey = eq("unknown"),
            correlationId = anyOrNull()
        )
    }

    @Test
    @DisplayName("TC12: recordValidationFailure() — eventService.record() throws exception → caught and logged (fail-safe)")
    fun shouldSwallowExceptionsOnValidationFailure() {
        // Given
        whenever(eventService.record<TokenValidationFailedEvent>(any(), any(), any(), any(), any(), anyOrNull()))
            .thenThrow(RuntimeException("Event store down"))

        val event = TokenValidationFailedEvent(
            reason = ValidationFailureReason.BLACKLISTED,
            tokenJti = "jti-fail",
            ipAddress = "127.0.0.1",
            userAgent = null,
            validatorName = null
        )

        // When — should NOT throw
        recorder.recordValidationFailure(event, correlationId = null)

        // Then — logged but not propagated (verified by no exception thrown)
    }
}

