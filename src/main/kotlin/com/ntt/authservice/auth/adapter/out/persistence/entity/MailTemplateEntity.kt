package com.ntt.authservice.auth.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Mail template entity — stores reusable email templates with placeholder support.
 * Template variables use {{key}} syntax, replaced at render time.
 */
@Entity
@Table(name = "mail_templates")
class MailTemplateEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    lateinit var code: String

    @Column(name = "name", nullable = false, length = 255)
    lateinit var name: String

    @Column(name = "subject_template", nullable = false, length = 500)
    lateinit var subjectTemplate: String

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    lateinit var bodyTemplate: String

    @Column(name = "language", nullable = false, length = 10)
    var language: String = "vi"
}
