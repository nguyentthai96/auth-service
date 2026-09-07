package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.entity.MailQueueEntity
import com.ntt.authservice.auth.adapter.out.persistence.entity.MailStatus
import com.ntt.authservice.auth.adapter.out.persistence.entity.MailTemplateEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailQueueRepository
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailTemplateRepository
import com.ntt.authservice.shared.config.SecurityProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.time.Instant

class MailJobSchedulerTest {

    private lateinit var scheduler: MailJobScheduler
    private lateinit var mailQueueRepository: MailQueueRepository
    private lateinit var mailTemplateRepository: MailTemplateRepository
    private lateinit var mailQueueService: MailQueueService
    private lateinit var javaMailSender: JavaMailSender
    private lateinit var securityProperties: SecurityProperties
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setUp() {
        mailQueueRepository = mock(MailQueueRepository::class.java)
        mailTemplateRepository = mock(MailTemplateRepository::class.java)
        mailQueueService = mock(MailQueueService::class.java)
        javaMailSender = mock(JavaMailSender::class.java)
        securityProperties = mock(SecurityProperties::class.java)
        meterRegistry = SimpleMeterRegistry()

        val queueProps = SecurityProperties.MailProperties.QueueProperties(
            batchSize = 10,
            pollIntervalMs = 5000,
            maxRetries = 3,
            baseRetryDelaySeconds = 30,
            cleanupAfterDays = 30
        )
        val templateProps = SecurityProperties.MailProperties.TemplateProperties(defaultLanguage = "vi")
        val mailProps = SecurityProperties.MailProperties(queue = queueProps, templates = templateProps)
        `when`(securityProperties.mail).thenReturn(mailProps)

        scheduler = MailJobScheduler(
            mailQueueRepository,
            mailTemplateRepository,
            mailQueueService,
            javaMailSender,
            securityProperties,
            meterRegistry
        )
    }

    @Test
    fun `processMailQueue skips when no pending items`() {
        `when`(mailQueueRepository.findPendingForProcessing(any(Instant::class.java), anyInt()))
            .thenReturn(emptyList())

        scheduler.processMailQueue()

        verify(javaMailSender, never()).send(any(SimpleMailMessage::class.java))
    }

    @Test
    fun `processMailQueue marks FAILED when template not found`() {
        val entity = mock(MailQueueEntity::class.java)
        `when`(entity.templateCode).thenReturn("MISSING_TEMPLATE")
        `when`(entity.retryCount).thenReturn(0)
        `when`(entity.maxRetries).thenReturn(3)
        `when`(mailQueueRepository.findPendingForProcessing(any(Instant::class.java), anyInt()))
            .thenReturn(listOf(entity))
        `when`(mailTemplateRepository.findByCodeAndActiveTrue("MISSING_TEMPLATE")).thenReturn(null)

        scheduler.processMailQueue()

        verify(entity).status = MailStatus.FAILED
        verify(entity).errorMessage = "Template not found: MISSING_TEMPLATE"
        verify(mailQueueRepository).save(entity)
    }

    @Test
    fun `processMailQueue sends email and marks SENT on success`() {
        val entity = mock(MailQueueEntity::class.java)
        `when`(entity.templateCode).thenReturn("NEW_DEVICE_LOGIN")
        `when`(entity.recipient).thenReturn("user@example.com")
        `when`(entity.templateData).thenReturn("{\"userName\":\"Test\"}")
        `when`(entity.retryCount).thenReturn(0)
        `when`(entity.maxRetries).thenReturn(3)

        val template = mock(MailTemplateEntity::class.java)
        `when`(mailTemplateRepository.findByCodeAndActiveTrue("NEW_DEVICE_LOGIN")).thenReturn(template)
        `when`(mailQueueService.renderTemplate(eq(template), any())).thenReturn(
            MailQueueService.RenderedMail(subject = "New Login", body = "Hello Test")
        )
        `when`(mailQueueRepository.findPendingForProcessing(any(Instant::class.java), anyInt()))
            .thenReturn(listOf(entity))

        scheduler.processMailQueue()

        verify(entity).status = MailStatus.SENT
        verify(javaMailSender).send(any(SimpleMailMessage::class.java))
    }

    @Test
    fun `processMailQueue retries on failure and marks FAILED when max exceeded`() {
        val entity = mock(MailQueueEntity::class.java)
        `when`(entity.templateCode).thenReturn("NEW_DEVICE_LOGIN")
        `when`(entity.recipient).thenReturn("user@example.com")
        `when`(entity.templateData).thenReturn("{}")
        `when`(entity.retryCount).thenReturn(2) // Already retried twice
        `when`(entity.maxRetries).thenReturn(3)

        val template = mock(MailTemplateEntity::class.java)
        `when`(mailTemplateRepository.findByCodeAndActiveTrue("NEW_DEVICE_LOGIN")).thenReturn(template)
        `when`(mailQueueService.renderTemplate(eq(template), any())).thenReturn(
            MailQueueService.RenderedMail(subject = "Test", body = "Test")
        )
        `when`(mailQueueRepository.findPendingForProcessing(any(Instant::class.java), anyInt()))
            .thenReturn(listOf(entity))
        doThrow(RuntimeException("SMTP error")).`when`(javaMailSender).send(any(SimpleMailMessage::class.java))

        scheduler.processMailQueue()

        // retryCount was 2, now 3 >= maxRetries(3) → should be FAILED
        verify(entity).retryCount = 3
        verify(mailQueueRepository).save(entity)
    }
}
