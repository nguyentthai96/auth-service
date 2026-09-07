package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.entity.MailQueueEntity
import com.ntt.authservice.auth.adapter.out.persistence.entity.MailStatus
import com.ntt.authservice.auth.adapter.out.persistence.entity.MailTemplateEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailQueueRepository
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailTemplateRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class MailQueueServiceTest {

    private lateinit var mailQueueService: MailQueueService
    private lateinit var mailQueueRepository: MailQueueRepository
    private lateinit var mailTemplateRepository: MailTemplateRepository
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        mailQueueRepository = mock(MailQueueRepository::class.java)
        mailTemplateRepository = mock(MailTemplateRepository::class.java)
        securityProperties = mock(SecurityProperties::class.java)

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

        mailQueueService = MailQueueService(
            mailQueueRepository,
            mailTemplateRepository,
            securityProperties,
            ObjectMapper()
        )
    }

    @Test
    fun `enqueue creates entity with correct fields`() {
        val captor = ArgumentCaptor.forClass(MailQueueEntity::class.java)
        `when`(mailQueueRepository.save(any())).thenAnswer { it.arguments[0] }

        mailQueueService.enqueue(
            recipient = "user@example.com",
            templateCode = "NEW_DEVICE_LOGIN",
            templateData = mapOf("userName" to "John", "deviceName" to "Chrome"),
            createdBy = 123L
        )

        verify(mailQueueRepository).save(captor.capture())
        val saved = captor.value
        assertEquals("user@example.com", saved.recipient)
        assertEquals("NEW_DEVICE_LOGIN", saved.templateCode)
        assertEquals(MailStatus.PENDING, saved.status)
        assertEquals(3, saved.maxRetries)
        assertTrue(saved.templateData.contains("John"))
    }

    @Test
    fun `renderTemplate substitutes placeholders correctly`() {
        val template = mock(MailTemplateEntity::class.java)
        `when`(template.subjectTemplate).thenReturn("New login from {{deviceName}}")
        `when`(template.bodyTemplate).thenReturn("Hello {{userName}}, device {{deviceName}} at {{loginTime}}")

        val data = mapOf(
            "userName" to "Alice" as Any,
            "deviceName" to "Firefox / macOS" as Any,
            "loginTime" to "2024-01-01T12:00:00Z" as Any
        )

        val result = mailQueueService.renderTemplate(template, data)

        assertEquals("New login from Firefox / macOS", result.subject)
        assertTrue(result.body.contains("Hello Alice"))
        assertTrue(result.body.contains("Firefox / macOS"))
        assertTrue(result.body.contains("2024-01-01T12:00:00Z"))
    }

    @Test
    fun `enqueue with null createdBy works`() {
        `when`(mailQueueRepository.save(any())).thenAnswer { it.arguments[0] }

        mailQueueService.enqueue(
            recipient = "system@example.com",
            templateCode = "SYSTEM_ALERT",
            templateData = mapOf("message" to "test")
        )

        verify(mailQueueRepository).save(any())
    }
}
