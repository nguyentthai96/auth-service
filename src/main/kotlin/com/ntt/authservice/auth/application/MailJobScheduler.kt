package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.entity.MailStatus
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailQueueRepository
import com.ntt.authservice.auth.adapter.out.persistence.repository.MailTemplateRepository
import com.ntt.authservice.shared.config.SecurityProperties
import io.micrometer.core.instrument.MeterRegistry
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Mail job scheduler — polls mail_queue and sends emails via SMTP.
 * Pattern: mirrors OutboxPoller (SELECT FOR UPDATE SKIP LOCKED).
 *
 * Retry strategy: exponential backoff (baseDelay × 2^retryCount).
 * After max retries → status = FAILED.
 */
@Component
class MailJobScheduler(
    private val mailQueueRepository: MailQueueRepository,
    private val mailTemplateRepository: MailTemplateRepository,
    private val mailQueueService: MailQueueService,
    private val javaMailSender: JavaMailSender,
    private val securityProperties: SecurityProperties,
    private val meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(MailJobScheduler::class.java)

    /**
     * Poll and process pending mails.
     * Runs at fixed delay — configurable via app.security.mail.queue.poll-interval-ms.
     */
    @Scheduled(fixedDelayString = "\${app.security.mail.queue.poll-interval-ms:5000}")
    @Transactional
    fun processMailQueue() {
        val props = securityProperties.mail.queue
        val now = Instant.now()

        val pendingMails = mailQueueRepository.findPendingForProcessing(now, props.batchSize)
        if (pendingMails.isEmpty()) return

        log.debug("MAIL_QUEUE Processing {} pending mails", pendingMails.size)

        for (mail in pendingMails) {
            try {
                mail.status = MailStatus.PROCESSING

                // Render template if not already rendered
                if (mail.bodyRendered == null) {
                    val template = mailTemplateRepository.findByCodeAndActiveTrue(mail.templateCode)
                    if (template == null) {
                        log.error("Mail template not found: code={}, mailId={}", mail.templateCode, mail.id)
                        mail.status = MailStatus.FAILED
                        mail.errorMessage = "Template not found: ${mail.templateCode}"
                        meterRegistry.counter("mail.send", "result", "template_not_found").increment()
                        continue
                    }

                    val data = com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(mail.templateData, Map::class.java) as Map<String, Any>
                    val rendered = mailQueueService.renderTemplate(template, data)
                    mail.subject = rendered.subject
                    mail.bodyRendered = rendered.body
                }

                // Send via SMTP
                val message: MimeMessage = javaMailSender.createMimeMessage()
                val helper = MimeMessageHelper(message, true, "UTF-8")
                helper.setTo(mail.recipient)
                helper.setSubject(mail.subject ?: "")
                helper.setText(mail.bodyRendered ?: "", false)

                javaMailSender.send(message)

                // Success
                mail.status = MailStatus.SENT
                mail.sentAt = Instant.now()
                meterRegistry.counter("mail.send", "result", "success").increment()
                log.info("MAIL_SENT id={}, recipient={}, template={}", mail.id, mail.recipient, mail.templateCode)

            } catch (e: Exception) {
                log.warn("MAIL_SEND_FAILED id={}, attempt={}, error={}", mail.id, mail.retryCount + 1, e.message)

                mail.retryCount++
                if (mail.retryCount >= mail.maxRetries) {
                    mail.status = MailStatus.FAILED
                    mail.errorMessage = e.message?.take(1000)
                    meterRegistry.counter("mail.send", "result", "failed_permanent").increment()
                } else {
                    // Exponential backoff: baseDelay × 2^retryCount
                    val delaySeconds = securityProperties.mail.queue.baseRetryDelaySeconds *
                            (1L shl mail.retryCount)
                    mail.status = MailStatus.PENDING
                    mail.nextRetryAt = Instant.now().plusSeconds(delaySeconds)
                    mail.errorMessage = e.message?.take(1000)
                    meterRegistry.counter("mail.send", "result", "retry").increment()
                }
            }
        }
    }

    /**
     * Cleanup old sent mails — runs daily.
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    @Transactional
    fun cleanupOldMails() {
        val cutoff = Instant.now().minusSeconds(
            securityProperties.mail.queue.cleanupAfterDays * 86400
        )
        val deleted = mailQueueRepository.deleteOldSentMails(cutoff)
        if (deleted > 0) {
            log.info("MAIL_CLEANUP Deleted {} old sent mails", deleted)
        }
    }
}
