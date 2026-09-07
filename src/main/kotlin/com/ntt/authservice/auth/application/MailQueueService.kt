package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.entity.MailQueueEntity
import com.ntt.authservice.auth.adapter.out.persistence.entity.MailStatus
import com.ntt.authservice.auth.adapter.out.persistence.entity.MailTemplateEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailQueueRepository
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailTemplateRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Mail queue service — enqueues email requests within the same @Transactional
 * as the business operation (Transactional Outbox pattern).
 */
@Service
class MailQueueService(
    private val mailQueueRepository: MailQueueRepository,
    private val mailTemplateRepository: MailTemplateRepository,
    private val securityProperties: SecurityProperties,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(MailQueueService::class.java)

    /**
     * Enqueue an email for async delivery.
     * Must be called within an existing @Transactional.
     *
     * @param recipient Email address
     * @param templateCode Template code (e.g., "NEW_DEVICE_LOGIN")
     * @param templateData Map of placeholder values for template rendering
     * @param createdBy User ID who triggered the email (nullable for system emails)
     */
    fun enqueue(
        recipient: String,
        templateCode: String,
        templateData: Map<String, Any>,
        createdBy: Long? = null
    ): MailQueueEntity {
        val entity = MailQueueEntity().apply {
            this.recipient = recipient
            this.templateCode = templateCode
            this.templateData = objectMapper.writeValueAsString(templateData)
            this.status = MailStatus.PENDING
            this.maxRetries = securityProperties.mail.queue.maxRetries
            this.createdBy = createdBy?.toString()
        }

        val saved = mailQueueRepository.save(entity)
        log.debug("Mail enqueued: id={}, template={}, recipient={}", saved.id, templateCode, recipient)
        return saved
    }

    /**
     * Render a template by substituting {{key}} placeholders with values.
     */
    fun renderTemplate(template: MailTemplateEntity, data: Map<String, Any>): RenderedMail {
        val subject = substitutePlaceholders(template.subjectTemplate, data)
        val body = substitutePlaceholders(template.bodyTemplate, data)
        return RenderedMail(subject = subject, body = body)
    }

    private fun substitutePlaceholders(template: String, data: Map<String, Any>): String {
        var result = template
        data.forEach { (key, value) ->
            result = result.replace("{{$key}}", value.toString())
        }
        return result
    }

    data class RenderedMail(val subject: String, val body: String)
}
