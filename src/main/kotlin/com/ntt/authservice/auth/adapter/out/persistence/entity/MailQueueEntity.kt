package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Mail queue entity — transactional outbox for async email delivery.
 * Records are created within the same @Transactional as the business operation,
 * then processed asynchronously by MailJobScheduler.
 *
 * Pattern: Mirrors EventOutboxEntity — poll + process + status update.
 */
@Entity
@Table(name = "mail_queue")
class MailQueueEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "recipient", nullable = false, length = 255)
    lateinit var recipient: String

    @Column(name = "subject", length = 500)
    var subject: String? = null

    @Column(name = "template_code", nullable = false, length = 100)
    lateinit var templateCode: String

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_data", nullable = false, columnDefinition = "jsonb")
    var templateData: String = "{}"

    @Column(name = "body_rendered", columnDefinition = "TEXT")
    var bodyRendered: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MailStatus = MailStatus.PENDING

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = 3

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null
}
