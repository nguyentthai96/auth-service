package com.ntt.authservice.auth.adapter.out.event

import com.ntt.authservice.auth.adapter.out.persistence.entity.EventOutboxEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.EventOutboxJpaRepository
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

/**
 * Unit tests for OutboxPoller — transactional outbox relay to Kafka.
 * Tests polling, publishing, timeout handling, and retry logic.
 *
 * FR-007: Event publishing via transactional outbox
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("OutboxPoller Tests")
class OutboxPollerTest {

    @Mock private lateinit var outboxRepository: EventOutboxJpaRepository
    @Mock private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private lateinit var poller: OutboxPoller

    private val batchSize = 50
    private val maxRetries = 3
    private val kafkaTimeoutMs = 5000L

    @BeforeEach
    fun setUp() {
        poller = OutboxPoller(
            outboxRepository = outboxRepository,
            kafkaTemplate = kafkaTemplate,
            batchSize = batchSize,
            maxRetries = maxRetries,
            kafkaTimeoutMs = kafkaTimeoutMs
        )
    }

    private fun createOutboxEntry(
        id: Long = 1L,
        topic: String = "iam.token.issued",
        partitionKey: String = "42",
        payload: String = """{"type":"test"}""",
        retryCount: Int = 0
    ): EventOutboxEntity {
        return EventOutboxEntity().apply {
            this.id = id
            this.aggregateType = "User"
            this.aggregateId = 42L
            this.eventType = "iam.token.issued"
            this.topic = topic
            this.partitionKey = partitionKey
            this.payload = payload
            this.status = "PENDING"
            this.retryCount = retryCount
        }
    }

    private fun mockKafkaSuccess(): CompletableFuture<SendResult<String, String>> {
        val metadata = RecordMetadata(TopicPartition("test", 0), 0, 0, 0, 0, 0)
        val sendResult = SendResult<String, String>(null, metadata)
        return CompletableFuture.completedFuture(sendResult)
    }

    @Test
    @DisplayName("TC1: Empty outbox → no Kafka publish, no errors")
    fun shouldDoNothingWhenOutboxEmpty() {
        // Given
        whenever(outboxRepository.findPendingForUpdate(batchSize)).thenReturn(emptyList())

        // When
        poller.pollAndPublish()

        // Then
        verify(kafkaTemplate, never()).send(any<String>(), any(), any<String>())
    }

    @Test
    @DisplayName("TC2: Single outbox entry → publish to Kafka → mark as PUBLISHED")
    fun shouldPublishSingleEntry() {
        // Given
        val entry = createOutboxEntry()
        whenever(outboxRepository.findPendingForUpdate(batchSize)).thenReturn(listOf(entry))
        whenever(kafkaTemplate.send(any<String>(), any(), any<String>())).thenReturn(mockKafkaSuccess())
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        poller.pollAndPublish()

        // Then
        verify(kafkaTemplate).send(eq("iam.token.issued"), eq("42"), any())
        assertEquals("PUBLISHED", entry.status)
        assertNotNull(entry.publishedAt)
        verify(outboxRepository).save(entry)
    }

    @Test
    @DisplayName("TC3: Batch of entries → all published in single poll cycle")
    fun shouldPublishBatchEntries() {
        // Given
        val entries = (1L..5L).map { createOutboxEntry(id = it) }
        whenever(outboxRepository.findPendingForUpdate(batchSize)).thenReturn(entries)
        whenever(kafkaTemplate.send(any<String>(), any(), any<String>())).thenReturn(mockKafkaSuccess())
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        poller.pollAndPublish()

        // Then
        verify(kafkaTemplate, times(5)).send(any<String>(), any(), any<String>())
        entries.forEach { assertEquals("PUBLISHED", it.status) }
    }

    @Test
    @DisplayName("TC5: Kafka publish timeout → entry stays PENDING, no retry count increment")
    fun shouldNotIncrementRetryOnTimeout() {
        // Given
        val entry = createOutboxEntry()
        whenever(outboxRepository.findPendingForUpdate(batchSize)).thenReturn(listOf(entry))

        val timeoutFuture = CompletableFuture<SendResult<String, String>>()
        timeoutFuture.completeExceptionally(TimeoutException("Kafka timeout"))
        whenever(kafkaTemplate.send(any<String>(), any(), any<String>())).thenReturn(timeoutFuture)

        // When
        poller.pollAndPublish()

        // Then — entry stays PENDING, retryCount NOT incremented
        assertEquals("PENDING", entry.status)
        assertEquals(0, entry.retryCount)
        // save NOT called for timeout (entry remains unchanged)
    }

    @Test
    @DisplayName("TC6: Kafka publish confirmed failure → retry count incremented")
    fun shouldIncrementRetryOnFailure() {
        // Given
        val entry = createOutboxEntry(retryCount = 0)
        whenever(outboxRepository.findPendingForUpdate(batchSize)).thenReturn(listOf(entry))

        val failFuture = CompletableFuture<SendResult<String, String>>()
        failFuture.completeExceptionally(RuntimeException("Kafka broker down"))
        whenever(kafkaTemplate.send(any<String>(), any(), any<String>())).thenReturn(failFuture)
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        poller.pollAndPublish()

        // Then
        assertEquals(1, entry.retryCount)
        assertEquals("PENDING", entry.status) // Not yet FAILED (retryCount < maxRetries)
        verify(outboxRepository).save(entry)
    }

    @Test
    @DisplayName("TC7: Entry with retryCount >= maxRetries → marked as FAILED")
    fun shouldMarkAsFailedWhenMaxRetriesExceeded() {
        // Given — entry already at maxRetries - 1
        val entry = createOutboxEntry(retryCount = 2) // After increment will be 3 = maxRetries
        whenever(outboxRepository.findPendingForUpdate(batchSize)).thenReturn(listOf(entry))

        val failFuture = CompletableFuture<SendResult<String, String>>()
        failFuture.completeExceptionally(RuntimeException("Permanent failure"))
        whenever(kafkaTemplate.send(any<String>(), any(), any<String>())).thenReturn(failFuture)
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        poller.pollAndPublish()

        // Then
        assertEquals(3, entry.retryCount)
        assertEquals("FAILED", entry.status)
        verify(outboxRepository).save(entry)
    }

    @Test
    @DisplayName("TC8: Mixed batch — some succeed, some fail")
    fun shouldHandleMixedBatch() {
        // Given
        val successEntry = createOutboxEntry(id = 1L)
        val failEntry = createOutboxEntry(id = 2L)
        whenever(outboxRepository.findPendingForUpdate(batchSize))
            .thenReturn(listOf(successEntry, failEntry))

        val successFuture = mockKafkaSuccess()
        val failFuture = CompletableFuture<SendResult<String, String>>()
        failFuture.completeExceptionally(RuntimeException("Kafka error"))

        whenever(kafkaTemplate.send(any<String>(), any(), any<String>()))
            .thenReturn(successFuture)
            .thenReturn(failFuture)
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        poller.pollAndPublish()

        // Then
        assertEquals("PUBLISHED", successEntry.status)
        assertEquals(1, failEntry.retryCount)
    }
}
