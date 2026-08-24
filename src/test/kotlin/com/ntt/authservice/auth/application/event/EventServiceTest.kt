package com.ntt.authservice.auth.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.ntt.authservice.auth.application.port.out.DomainEvent
import com.ntt.authservice.auth.application.port.out.EventStorePort
import com.ntt.authservice.auth.application.port.out.OutboxPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

/**
 * Unit tests for EventService — event recording pipeline (event store + outbox).
 * Verifies transactional event recording with correct envelope creation.
 *
 * FR-007: Event recording, FR-009: Token lifecycle events
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("EventService Tests")
class EventServiceTest {

    @Mock private lateinit var eventStorePort: EventStorePort
    @Mock private lateinit var outboxPort: OutboxPort

    private lateinit var eventService: EventService
    private val objectMapper = ObjectMapper().apply { registerModule(JavaTimeModule()) }

    /** Simple test domain event for verification. */
    data class TestEvent(
        val userId: Long,
        val action: String
    ) : DomainEvent {
        override val eventType: String = "test.event"
    }

    @BeforeEach
    fun setUp() {
        eventService = EventService(
            eventStorePort = eventStorePort,
            outboxPort = outboxPort,
            objectMapper = objectMapper,
            eventSource = "auth-service"
        )
    }

    @Test
    @DisplayName("TC1: record() creates EventEnvelope with correct aggregate fields")
    fun shouldCreateEventEnvelopeWithCorrectFields() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 42L)).thenReturn(1L)
        val testEvent = TestEvent(userId = 42L, action = "login")

        // When
        eventService.record(
            aggregateType = "User",
            aggregateId = 42L,
            event = testEvent,
            topic = "test.topic",
            partitionKey = "42"
        )

        // Then
        verify(eventStorePort).append(
            aggregateType = eq("User"),
            aggregateId = eq(42L),
            eventType = eq("test.event"),
            schemaVersion = eq(1),
            sequenceNumber = eq(1L),
            payload = argThat { payload ->
                payload.contains("\"type\":\"test.event\"") &&
                payload.contains("\"source\":\"auth-service\"") &&
                payload.contains("\"specversion\":\"1.0\"")
            },
            metadata = eq("{}")
        )
    }

    @Test
    @DisplayName("TC2: record() calls eventStorePort.append()")
    fun shouldPersistToEventStore() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 1L)).thenReturn(5L)
        val testEvent = TestEvent(userId = 1L, action = "register")

        // When
        eventService.record("User", 1L, testEvent, "test.topic", "1")

        // Then
        verify(eventStorePort).append(
            aggregateType = eq("User"),
            aggregateId = eq(1L),
            eventType = eq("test.event"),
            schemaVersion = eq(1),
            sequenceNumber = eq(5L),
            payload = any(),
            metadata = eq("{}")
        )
    }

    @Test
    @DisplayName("TC3: record() calls outboxPort.insert()")
    fun shouldInsertIntoOutbox() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 1L)).thenReturn(1L)
        val testEvent = TestEvent(userId = 1L, action = "test")

        // When
        eventService.record("User", 1L, testEvent, "iam.test.topic", "partition-1")

        // Then
        verify(outboxPort).insert(
            aggregateType = eq("User"),
            aggregateId = eq(1L),
            eventType = eq("test.event"),
            topic = eq("iam.test.topic"),
            partitionKey = eq("partition-1"),
            payload = any()
        )
    }

    @Test
    @DisplayName("TC4: Both port calls happen in same invocation")
    fun shouldCallBothPortsInSameInvocation() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 1L)).thenReturn(1L)
        val testEvent = TestEvent(userId = 1L, action = "dual")

        // When
        eventService.record("User", 1L, testEvent, "topic", "key")

        // Then
        verify(eventStorePort, times(1)).append(any(), any(), any(), any(), any(), any(), any())
        verify(outboxPort, times(1)).insert(any(), any(), any(), any(), any(), any())
    }

    @Test
    @DisplayName("TC5: eventStorePort.append() failure propagates exception")
    fun shouldPropagateEventStoreFailure() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 1L)).thenReturn(1L)
        whenever(eventStorePort.append(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("Event store write failed"))

        val testEvent = TestEvent(userId = 1L, action = "fail-store")

        // When/Then
        assertThrows<RuntimeException> {
            eventService.record("User", 1L, testEvent, "topic", "key")
        }
    }

    @Test
    @DisplayName("TC6: outboxPort.insert() failure propagates exception")
    fun shouldPropagateOutboxFailure() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 1L)).thenReturn(1L)
        whenever(outboxPort.insert(any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("Outbox write failed"))

        val testEvent = TestEvent(userId = 1L, action = "fail-outbox")

        // When/Then
        assertThrows<RuntimeException> {
            eventService.record("User", 1L, testEvent, "topic", "key")
        }
    }

    @Test
    @DisplayName("TC7: Sequence number auto-incremented for same aggregate")
    fun shouldUseNextSequenceNumber() {
        // Given — first call returns 1, second returns 2
        whenever(eventStorePort.getNextSequenceNumber("User", 1L))
            .thenReturn(1L)
            .thenReturn(2L)

        val event1 = TestEvent(userId = 1L, action = "first")
        val event2 = TestEvent(userId = 1L, action = "second")

        // When
        eventService.record("User", 1L, event1, "topic", "key")
        eventService.record("User", 1L, event2, "topic", "key")

        // Then — verify sequence numbers
        verify(eventStorePort).append(any(), any(), any(), any(), eq(1L), any(), any())
        verify(eventStorePort).append(any(), any(), any(), any(), eq(2L), any(), any())
    }

    @Test
    @DisplayName("TC-extra: correlationId auto-generated when null")
    fun shouldAutoGenerateCorrelationIdWhenNull() {
        // Given
        whenever(eventStorePort.getNextSequenceNumber("User", 1L)).thenReturn(1L)
        val testEvent = TestEvent(userId = 1L, action = "auto-corr")

        // When
        eventService.record("User", 1L, testEvent, "topic", "key", correlationId = null)

        // Then — correlationId present in payload (auto-generated UUID)
        verify(eventStorePort).append(
            any(), any(), any(), any(), any(),
            argThat { it.contains("\"correlationId\":\"") },
            any()
        )
    }
}
