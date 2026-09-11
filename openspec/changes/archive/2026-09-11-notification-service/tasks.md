<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "NEWBUILD", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: notification-service

> **Profile**: Command | NEWBUILD
> **Phase**: Phase 1 — Email only

---

## Layer 1: Project Bootstrap (notification-service)

- [x] **Task 1: Create notification-service project scaffold**
  - File: `services/notification-service/` | Action: [NEW]
  - FR: FR-020 — Standalone Spring Boot service
  - Pattern: Copy project structure from account-service
  - Subtasks:
    - [x] 1.1 Create `settings.gradle.kts` (rootProject.name = "notification-service", versionCatalog com.ntt:version-catalog, buildLogic com.ntt.build:build-logic)
    - [x] 1.2 Create `build.gradle.kts` (plugin ntt.spring-app-conventions, dependencies: base-web-starter, base-data-starter, common-log, spring-boot-starter-web/data-jpa/validation/actuator/mail, resilience4j-spring-boot3, flyway-core/postgresql, micrometer-registry-prometheus, jackson-module-kotlin, postgresql runtime)
    - [x] 1.3 Create `gradle.properties` (group=com.ntt, version=0.0.1-SNAPSHOT)
    - [x] 1.4 Create `NotificationServiceApplication.kt` (@SpringBootApplication, @EnableConfigurationProperties, @EnableScheduling)
    - [x] 1.5 Create `application.yml` (server.port=8082, spring.datasource notification-db config, spring.mail SMTP config, app.notification.* defaults, actuator endpoints)

---

## Layer 2: Domain Models

- [x] **Task 2: Create domain enums**
  - File: `notification/domain/model/NotificationChannel.kt` | Action: [NEW]
  - FR: FR-005 — Multi-channel routing
  - Pattern: Kotlin enum class
  - Values: EMAIL, SMS, PUSH, OTT

- [x] **Task 3: Create NotificationStatus enum**
  - File: `notification/domain/model/NotificationStatus.kt` | Action: [NEW]
  - FR: FR-013 — 7-state lifecycle
  - Pattern: Kotlin enum class
  - Values: PENDING, PROCESSING, SENT, DELIVERED, READ, FAILED, REVOKED, CANCELLED

- [x] **Task 4: Create NotificationPriority enum**
  - File: `notification/domain/model/NotificationPriority.kt` | Action: [NEW]
  - FR: FR-003 — Priority-based ordering
  - Values: LOW, NORMAL, HIGH, URGENT

---

## Layer 3: Persistence (Entities + Repositories)

- [x] **Task 5: Create NotificationQueueEntity**
  - File: `notification/adapter/out/persistence/entity/NotificationQueueEntity.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-001 — notification_queue table mapping
  - Fields: correlationId, channel (NotificationChannel), priority (NotificationPriority), recipient, subject, templateCode, templateData (JSONB), bodyRendered, status (NotificationStatus), retryCount, maxRetries, errorMessage, sentAt, deliveredAt, readAt, revokedAt, revokeReason, nextRetryAt, sourceService, createdBy
  - Dependencies: NotificationChannel, NotificationStatus, NotificationPriority enums

- [x] **Task 6: Create NotificationTemplateEntity**
  - File: `notification/adapter/out/persistence/entity/NotificationTemplateEntity.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-004 — Template storage
  - Fields: code (unique), name, channel (NotificationChannel), subjectTemplate, bodyTemplate, trackingMode, language, active

- [x] **Task 7: Create NotificationQueueRepository**
  - File: `notification/adapter/out/persistence/repository/NotificationQueueRepository.kt` | Action: [NEW]
  - FR: FR-003, FR-009, FR-018
  - Pattern: JpaRepository + native query SELECT FOR UPDATE SKIP LOCKED (from auth-service MailQueueRepository)
  - Methods: findPendingForProcessing(now, batchSize), deleteOldProcessedNotifications(cutoff)

- [x] **Task 8: Create NotificationTemplateRepository**
  - File: `notification/adapter/out/persistence/repository/NotificationTemplateRepository.kt` | Action: [NEW]
  - FR: FR-004
  - Methods: findByCodeAndActiveTrue(code)

---

## Layer 4: Flyway Migrations

- [x] **Task 9: Create V1__create_notification_queue.sql**
  - File: `src/main/resources/db/migration/V1__create_notification_queue.sql` | Action: [NEW]
  - FR: FR-001
  - Pattern: DDL from SRS Section 6 — notification_queue table
  - Indexes: idx_notif_queue_status_retry, idx_notif_queue_channel_status, idx_notif_queue_created, idx_notif_queue_correlation, idx_notif_queue_source

- [x] **Task 10: Create V2__create_notification_templates.sql**
  - File: `src/main/resources/db/migration/V2__create_notification_templates.sql` | Action: [NEW]
  - FR: FR-004
  - DDL: notification_template table + idx_notif_template_code_active index

- [x] **Task 11: Create V3__seed_templates.sql**
  - File: `src/main/resources/db/migration/V3__seed_templates.sql` | Action: [NEW]
  - FR: FR-004
  - Seed: NEW_DEVICE_LOGIN template (Vietnamese), PASSWORD_RESET template, OTP_VERIFICATION template

---

## Layer 5: Configuration

- [x] **Task 12: Create NotificationProperties**
  - File: `notification/config/NotificationProperties.kt` | Action: [NEW]
  - FR: FR-008, FR-023
  - Pattern: @ConfigurationProperties(prefix = "app.notification")
  - Nested: QueueProperties (batchSize, pollIntervalMs, maxRetries, baseRetryDelaySeconds, cleanupAfterDays), ChannelProperties (email/sms/push/ott → ChannelConfig(enabled, maxRetries))

---

## Layer 6: Error Handling

- [x] **Task 13: Create NotificationErrorCode enum**
  - File: `shared/exception/NotificationErrorCode.kt` | Action: [NEW]
  - FR: All error paths
  - Pattern: Mirrors AuthErrorCode from auth-service (enum with code, msgCode, description, httpStatus, toErrorCodeBase())
  - Values: ENQUEUE_FAILED, DUPLICATE, TEMPLATE_NOT_FOUND, CHANNEL_NOT_SUPPORTED, INVALID_STATUS_TRANSITION, NOT_FOUND, INVALID_REQUEST

- [x] **Task 14: Create NotificationException**
  - File: `shared/exception/NotificationException.kt` | Action: [NEW]
  - Base: `BusinessException` from `com.ntt.basecore.exception`
  - Pattern: Mirrors AuthException (bridge pattern → BusinessException → ErrorCodeBase)

- [x] **Task 15: Create GlobalExceptionHandler**
  - File: `shared/exception/GlobalExceptionHandler.kt` | Action: [NEW]
  - Pattern: @ControllerAdvice extends BaseControllerAdvice (base-core)
  - Handles: NotificationException → ProblemDetail response

---

## Layer 7: Application Services

- [x] **Task 16: Create TemplateRenderService**
  - File: `notification/application/TemplateRenderService.kt` | Action: [NEW]
  - FR: FR-004
  - Pattern: Pure function — {{key}} regex substitution on subject_template + body_template
  - Dependencies: NotificationTemplateRepository

- [x] **Task 17: Create NotificationSender interface**
  - File: `notification/application/port/out/NotificationSender.kt` | Action: [NEW]
  - FR: FR-005
  - Pattern: Strategy interface — channel(), send(notification), supports(channel)

- [x] **Task 18: Create SmtpEmailSender**
  - File: `notification/adapter/out/sender/SmtpEmailSender.kt` | Action: [NEW]
  - FR: FR-006, FR-023, FR-024
  - Pattern: @Component + @ConditionalOnProperty + CircuitBreaker
  - Dependencies: JavaMailSender, CircuitBreaker (Resilience4j)

- [x] **Task 19: Create NotificationDispatcher**
  - File: `notification/application/NotificationDispatcher.kt` | Action: [NEW]
  - FR: FR-005
  - Pattern: Strategy router — auto-discover NotificationSender beans, map by channel
  - Dependencies: List<NotificationSender>

- [x] **Task 20: Create NotificationJobScheduler**
  - File: `notification/application/NotificationJobScheduler.kt` | Action: [NEW]
  - FR: FR-003, FR-004, FR-006, FR-007, FR-008, FR-009, FR-010, FR-021
  - Pattern: @Scheduled(fixedDelayString), @Transactional — mirrors MailJobScheduler from auth-service
  - Source: `auth-service/.../MailJobScheduler.kt` (logic reference, not copy)
  - Dependencies: NotificationQueueRepository, TemplateRenderService, NotificationDispatcher, NotificationProperties, MeterRegistry
  - Logic: poll → render template → dispatch → update status (SENT/FAILED) → retry with exponential backoff

- [x] **Task 21: Create NotificationEnqueueService**
  - File: `notification/application/NotificationEnqueueService.kt` | Action: [NEW]
  - FR: FR-001, FR-002
  - Pattern: @Service, @Transactional — validate + INSERT into notification_queue
  - Dependencies: NotificationQueueRepository, ObjectMapper

- [x] **Task 22: Create NotificationRevokeService**
  - File: `notification/application/NotificationRevokeService.kt` | Action: [NEW]
  - FR: FR-014, FR-015
  - Pattern: @Service — status transition validation + update
  - Dependencies: NotificationQueueRepository

- [x] **Task 23: Create NotificationCleanupScheduler**
  - File: `notification/application/NotificationCleanupScheduler.kt` | Action: [NEW]
  - FR: FR-018
  - Pattern: @Scheduled(cron = "0 0 2 * * *") — daily cleanup old records
  - Dependencies: NotificationQueueRepository, NotificationProperties

---

## Layer 8: REST Controllers + DTOs

- [x] **Task 24: Create NotificationDtos**
  - File: `notification/adapter/in/web/dto/NotificationDtos.kt` | Action: [NEW]
  - FR: FR-001, FR-022
  - DTOs: EnqueueNotificationRequest, NotificationStatusResponse, RevokeRequest, RetryResponse
  - Pattern: data class + @Valid annotations (@NotBlank, @NotNull, @Size)

- [x] **Task 25: Create NotificationController**
  - File: `notification/adapter/in/web/NotificationController.kt` | Action: [NEW]
  - FR: FR-001, FR-014, FR-015, FR-019, FR-022
  - Endpoints: POST /api/v1/notifications, GET /{id}, POST /{id}/retry, POST /{id}/revoke, GET /api/v1/notifications (list)
  - Dependencies: NotificationEnqueueService, NotificationRevokeService, NotificationQueueRepository

---

## Layer 9: Observability

- [x] **Task 26: Configure Micrometer metrics**
  - File: `notification/application/NotificationJobScheduler.kt` | Action: [MODIFY] (add counters)
  - FR: FR-017
  - Counters: notification.send{channel, result}, notification.enqueue{source_service, channel}, notification.revoke{channel}
  - Pattern: MeterRegistry.counter() inline in scheduler + enqueue + revoke services

---

## Layer 10: notification-client (Shared Library)

- [x] **Task 27: Create notification-client subproject**
  - File: `services/notification-service/notification-client/` | Action: [NEW]
  - FR: FR-020
  - Subtasks:
    - [x] 27.1 Create `notification-client/build.gradle.kts` (java-library + maven-publish, dependencies: base-data-starter, spring-boot-starter-data-jpa, spring-boot-autoconfigure)
    - [x] 27.2 Create `NotificationPort.kt` — interface with enqueue(NotificationRequest): Long
    - [x] 27.3 Create `NotificationRequest.kt` — data class (recipient, templateCode, templateData, channel, priority, correlationId, sourceService, createdBy)
    - [x] 27.4 Create `JpaNotificationAdapter.kt` — @Component implements NotificationPort, INSERT into notification_queue via EntityManager
    - [x] 27.5 Create `NotificationClientAutoConfiguration.kt` — @AutoConfiguration, @ConditionalOnProperty, @EnableJpaRepositories
    - [x] 27.6 Create `spring.factories` or `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    - [x] 27.7 Update root `settings.gradle.kts` to include `:notification-client`
    - [x] 27.8 Run `./gradlew :notification-client:publishToMavenLocal`

---

## Layer 11: Auth-service Cleanup

- [x] **Task 28: Drop mail tables from auth-db**
  - File: `auth-service/src/main/resources/db/migration/V22__drop_mail_tables.sql` | Action: [NEW]
  - DDL: DROP TABLE IF EXISTS mail_queue; DROP TABLE IF EXISTS mail_templates;

- [x] **Task 29: Remove Mail* code from auth-service**
  - Files: Multiple | Action: [DELETE]
  - Delete: MailJobScheduler.kt, MailQueueService.kt, MailQueueEntity.kt, MailStatus.kt, MailTemplateEntity.kt, MailQueueRepository.kt, MailTemplateRepository.kt

- [x] **Task 30: Update SecurityProperties — remove MailProperties**
  - File: `auth-service/.../SecurityProperties.kt` | Action: [MODIFY]
  - Remove: MailProperties inner class + val mail property
  - FR: Cleanup

- [x] **Task 31: Update NewDeviceMailHandler → use NotificationPort**
  - File: `auth-service/.../NewDeviceMailHandler.kt` | Action: [MODIFY]
  - FR: FR-020
  - Change: Replace MailQueueService.enqueue() → NotificationPort.enqueue(NotificationRequest)
  - Dependencies: com.ntt.notification.client.NotificationPort

- [x] **Task 32: Add notification-client dependency to auth-service**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - Add: implementation("com.ntt:notification-client:0.0.1-SNAPSHOT")
  - Add to `application.yml`: app.notification.datasource.* config

---

## Layer 12: Build & Verify

- [x] **Task 33: Build notification-service**
  - Command: `cd services/notification-service && ./gradlew build`
  - Verify: BUILD SUCCESSFUL

- [x] **Task 34: Build auth-service (after cleanup)**
  - Command: `cd services/auth-service && ./gradlew build`
  - Verify: BUILD SUCCESSFUL, no Mail* references

---

## Summary

| Layer | Tasks | Total Files |
|-------|-------|-------------|
| Project Bootstrap | 1 (5 subtasks) | 5 |
| Domain Models | 3 | 3 |
| Persistence | 4 | 4 |
| Migrations | 3 | 3 |
| Configuration | 1 | 1 |
| Error Handling | 3 | 3 |
| Application Services | 8 | 8 |
| REST + DTOs | 2 | 2 |
| Observability | 1 | 0 (modify) |
| notification-client | 1 (8 subtasks) | 7 |
| Auth-service Cleanup | 5 | varies |
| Build & Verify | 2 | 0 |
| **Total** | **34 tasks** | **~36 new files** |
