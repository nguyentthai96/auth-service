# Design: notification-service

## 1. Architecture Overview

```
    ┌─────────────────┐         ┌──────────────────────────────────┐
    │  auth-service    │         │  notification-service             │
    │                  │         │                                   │
    │  ┌────────────┐  │ shared  │  ┌───────────────────────────┐   │
    │  │Notification│──┼──DB────►│  │ NotificationJobScheduler  │   │
    │  │Port (SDK)  │  │         │  │ @Scheduled poll            │   │
    │  └────────────┘  │         │  │ SELECT FOR UPDATE          │   │
    └──────────────────┘         │  │ SKIP LOCKED                │   │
                                 │  └────────────┬──────────────┘   │
    ┌─────────────────┐         │               │                   │
    │  account-service │         │  ┌────────────▼──────────────┐   │
    │  ┌────────────┐  │ shared  │  │ NotificationDispatcher    │   │
    │  │Notification│──┼──DB────►│  │ (Strategy Pattern router) │   │
    │  │Port (SDK)  │  │         │  └──┬─────┬─────┬─────┬─────┘   │
    │  └────────────┘  │         │     │     │     │     │          │
    └──────────────────┘         │  ┌──▼──┐┌─▼──┐┌─▼──┐┌─▼──┐     │
                                 │  │Email││SMS ││Push││OTT │     │
                                 │  │Send ││Send││Send││Send│     │
                                 │  └──┬──┘└─┬──┘└─┬──┘└─┬──┘     │
                                 │     │     │     │     │          │
                                 │  ┌──▼─────▼─────▼─────▼──┐      │
                                 │  │ CircuitBreaker (R4J)   │      │
                                 │  └────────────────────────┘      │
                                 │                                   │
    ┌────────────────────┐       │  ┌─────────────────────────┐     │
    │ REST API           │◄──────┤  │ NotificationController  │     │
    │ (enqueue, status,  │       │  │ WebhookController       │     │
    │  retry, revoke)    │       │  └─────────────────────────┘     │
    └────────────────────┘       └──────────────────────────────────┘
                                            │
                                    ┌───────▼───────┐
                                    │notification-db│
                                    │ (PostgreSQL)  │
                                    └───────────────┘
```

## 2. Package Structure

```
com.ntt.notificationservice/
├── NotificationServiceApplication.kt          ← @SpringBootApplication
│
├── notification/                               ← Bounded Context
│   ├── domain/
│   │   └── model/
│   │       ├── NotificationChannel.kt          ← enum: EMAIL, SMS, PUSH, OTT
│   │       ├── NotificationStatus.kt           ← enum: 7 states
│   │       └── NotificationPriority.kt         ← enum: LOW, NORMAL, HIGH, URGENT
│   │
│   ├── application/
│   │   ├── NotificationJobScheduler.kt         ← @Scheduled poll + process
│   │   ├── NotificationDispatcher.kt           ← Strategy router
│   │   ├── NotificationEnqueueService.kt       ← Enqueue logic (REST API path)
│   │   ├── NotificationRevokeService.kt        ← Revoke/cancel logic
│   │   ├── NotificationCleanupScheduler.kt     ← Daily cleanup @Scheduled
│   │   ├── TemplateRenderService.kt            ← {{key}} substitution
│   │   └── port/
│   │       └── out/
│   │           └── NotificationSender.kt       ← Strategy interface
│   │
│   ├── adapter/
│   │   ├── in/
│   │   │   └── web/
│   │   │       ├── NotificationController.kt   ← REST endpoints
│   │   │       ├── WebhookController.kt        ← Delivery callbacks (Phase 3)
│   │   │       └── dto/
│   │   │           └── NotificationDtos.kt     ← Request/Response DTOs
│   │   │
│   │   └── out/
│   │       ├── persistence/
│   │       │   ├── entity/
│   │       │   │   ├── NotificationQueueEntity.kt
│   │       │   │   └── NotificationTemplateEntity.kt
│   │       │   └── repository/
│   │       │       ├── NotificationQueueRepository.kt
│   │       │       └── NotificationTemplateRepository.kt
│   │       │
│   │       └── sender/
│   │           └── SmtpEmailSender.kt          ← Phase 1 implementation
│   │
│   └── config/
│       └── NotificationProperties.kt          ← @ConfigurationProperties
│
└── shared/
    ├── exception/
    │   ├── NotificationException.kt           ← extends BusinessException
    │   ├── NotificationErrorCode.kt           ← enum error codes
    │   └── GlobalExceptionHandler.kt          ← extends BaseControllerAdvice
    └── config/
        └── MailConfig.kt                      ← JavaMailSender config
```

## 3. Component Details

### 3.1 NotificationQueueEntity

```kotlin
// com.ntt.notificationservice.notification.adapter.out.persistence.entity
@Entity
@Table(name = "notification_queue")
class NotificationQueueEntity : SnowflakePersistentAuditableEntity() {
    // Fields from SRS Section 6 — notification_queue table
    // Extends SnowflakePersistentAuditableEntity (base-core: id, createdAt, updatedAt)
}
```

- **Base class**: `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity`
- **FR coverage**: FR-001, FR-002, FR-013

### 3.2 NotificationTemplateEntity

```kotlin
// com.ntt.notificationservice.notification.adapter.out.persistence.entity
@Entity
@Table(name = "notification_template")
class NotificationTemplateEntity : SnowflakePersistentAuditableEntity() {
    // Fields from SRS Section 6 — notification_template table
    // Includes tracking_mode field for Phase 3
}
```

- **FR coverage**: FR-004, FR-012

### 3.3 NotificationQueueRepository

```kotlin
// com.ntt.notificationservice.notification.adapter.out.persistence.repository
@Repository
interface NotificationQueueRepository : JpaRepository<NotificationQueueEntity, Long> {
    @Query(nativeQuery = true, value = """
        SELECT * FROM notification_queue 
        WHERE status = 'PENDING' 
          AND (next_retry_at IS NULL OR next_retry_at <= :now) 
        ORDER BY 
          CASE priority 
            WHEN 'URGENT' THEN 0 
            WHEN 'HIGH' THEN 1 
            WHEN 'NORMAL' THEN 2 
            WHEN 'LOW' THEN 3 
          END, 
          created_at ASC 
        LIMIT :batchSize 
        FOR UPDATE SKIP LOCKED
    """)
    fun findPendingForProcessing(now: Instant, batchSize: Int): List<NotificationQueueEntity>
    
    @Modifying
    @Query("DELETE FROM NotificationQueueEntity n WHERE n.status IN ('SENT','DELIVERED','READ') AND n.sentAt < :cutoff")
    fun deleteOldProcessedNotifications(cutoff: Instant): Int
}
```

- **FR coverage**: FR-003, FR-009, FR-018

### 3.4 NotificationJobScheduler

```kotlin
// com.ntt.notificationservice.notification.application
@Component
class NotificationJobScheduler(
    private val notificationQueueRepository: NotificationQueueRepository,
    private val templateRepository: NotificationTemplateRepository,
    private val templateRenderService: TemplateRenderService,
    private val dispatcher: NotificationDispatcher,
    private val properties: NotificationProperties,
    private val meterRegistry: MeterRegistry
) {
    @Scheduled(fixedDelayString = "\${app.notification.queue.poll-interval-ms:5000}")
    @Transactional
    fun processQueue() { /* poll → render → dispatch → update status */ }
    
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    fun cleanupOldNotifications() { /* daily cleanup */ }
}
```

- **Pattern**: Mirrors MailJobScheduler from auth-service
- **FR coverage**: FR-003, FR-004, FR-006, FR-007, FR-008, FR-009, FR-010, FR-018, FR-021

### 3.5 NotificationDispatcher

```kotlin
// com.ntt.notificationservice.notification.application
@Component
class NotificationDispatcher(
    senders: List<NotificationSender>
) {
    private val senderMap: Map<NotificationChannel, NotificationSender> = 
        senders.associateBy { it.channel() }
    
    fun dispatch(notification: NotificationQueueEntity) {
        val sender = senderMap[notification.channel] 
            ?: throw NotificationException(NotificationErrorCode.CHANNEL_NOT_SUPPORTED)
        sender.send(notification)
    }
}
```

- **Pattern**: Strategy Pattern — auto-discover all NotificationSender beans
- **FR coverage**: FR-005

### 3.6 NotificationSender Interface

```kotlin
// com.ntt.notificationservice.notification.application.port.out
interface NotificationSender {
    fun channel(): NotificationChannel
    fun send(notification: NotificationQueueEntity)
    fun supports(channel: NotificationChannel): Boolean = channel() == channel
}
```

### 3.7 SmtpEmailSender

```kotlin
// com.ntt.notificationservice.notification.adapter.out.sender
@Component
@ConditionalOnProperty(prefix = "app.notification.channels.email", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SmtpEmailSender(
    private val javaMailSender: JavaMailSender,
    private val properties: NotificationProperties,
    @Qualifier("emailCircuitBreaker") private val circuitBreaker: CircuitBreaker
) : NotificationSender {
    override fun channel() = NotificationChannel.EMAIL
    override fun send(notification: NotificationQueueEntity) { /* SMTP send with circuit breaker */ }
}
```

- **FR coverage**: FR-006, FR-023, FR-024

### 3.8 NotificationProperties

```kotlin
// com.ntt.notificationservice.notification.config
@ConfigurationProperties(prefix = "app.notification")
data class NotificationProperties(
    val queue: QueueProperties = QueueProperties(),
    val channels: ChannelProperties = ChannelProperties()
) {
    data class QueueProperties(
        val batchSize: Int = 10,
        val pollIntervalMs: Long = 5000,
        val maxRetries: Int = 3,
        val baseRetryDelaySeconds: Long = 30,
        val cleanupAfterDays: Long = 30
    )
    data class ChannelProperties(
        val email: ChannelConfig = ChannelConfig(enabled = true),
        val sms: ChannelConfig = ChannelConfig(enabled = false),
        val push: ChannelConfig = ChannelConfig(enabled = false),
        val ott: ChannelConfig = ChannelConfig(enabled = false)
    )
    data class ChannelConfig(
        val enabled: Boolean = false,
        val maxRetries: Int = 3
    )
}
```

- **FR coverage**: FR-008, FR-023

### 3.9 NotificationController

```kotlin
// com.ntt.notificationservice.notification.adapter.in.web
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val enqueueService: NotificationEnqueueService,
    private val revokeService: NotificationRevokeService,
    private val queueRepository: NotificationQueueRepository
) {
    @PostMapping fun enqueue(@Valid @RequestBody request: EnqueueNotificationRequest): ResponseEntity<*>
    @GetMapping("/{id}") fun getStatus(@PathVariable id: Long): ResponseEntity<*>
    @PostMapping("/{id}/retry") fun retry(@PathVariable id: Long): ResponseEntity<*>
    @PostMapping("/{id}/revoke") fun revoke(@PathVariable id: Long, @RequestBody request: RevokeRequest): ResponseEntity<*>
}
```

- **FR coverage**: FR-001, FR-014, FR-015, FR-019, FR-022

### 3.10 notification-client Module

```
notification-client/
├── build.gradle.kts
└── src/main/kotlin/com/ntt/notification/client/
    ├── NotificationPort.kt                  ← Interface
    ├── NotificationRequest.kt               ← Data class
    ├── NotificationClientAutoConfiguration.kt ← Auto-config
    └── JpaNotificationAdapter.kt            ← Impl: INSERT into notification_queue
```

```kotlin
// com.ntt.notification.client
interface NotificationPort {
    fun enqueue(request: NotificationRequest): Long
}

data class NotificationRequest(
    val recipient: String,
    val templateCode: String,
    val templateData: Map<String, Any> = emptyMap(),
    val channel: String = "EMAIL",
    val priority: String = "NORMAL",
    val correlationId: String? = null,
    val sourceService: String? = null,
    val createdBy: Long? = null
)
```

- **Publish**: mavenLocal (`./gradlew publishToMavenLocal`)
- **FR coverage**: FR-020

## 4. Error Handling

```kotlin
// com.ntt.notificationservice.shared.exception
open class NotificationException(
    val errorCode: NotificationErrorCode,
    override val message: String = errorCode.toErrorCodeBase().getDesc() ?: "",
    cause: Throwable? = null
) : BusinessException(errorCode.toErrorCodeBase())

enum class NotificationErrorCode(
    private val code: String,
    private val msgCode: String,
    private val description: String
) {
    ENQUEUE_FAILED("NOTIF-001", "notification.enqueue.failed", "Failed to enqueue notification"),
    DUPLICATE("NOTIF-002", "notification.duplicate", "Duplicate correlation_id"),
    TEMPLATE_NOT_FOUND("NOTIF-004", "notification.template.not_found", "Template code not found"),
    CHANNEL_NOT_SUPPORTED("NOTIF-005", "notification.channel.not_supported", "Channel type not supported"),
    INVALID_STATUS_TRANSITION("NOTIF-013", "notification.status.invalid_transition", "Invalid status transition"),
    NOT_FOUND("NOTIF-014", "notification.not_found", "Notification not found"),
    INVALID_REQUEST("NOTIF-022", "notification.request.invalid", "Invalid notification request");
    
    // Bridge to base-core ErrorCodeBase
    fun toErrorCodeBase(): ErrorCodeBase { ... }
}
```

- **Pattern**: Mirrors AuthException/AuthErrorCode from auth-service
- **FR coverage**: All error paths

## 5. Dependency Map

```
notification-service
├── com.ntt:platform:0.0.1-SNAPSHOT (BOM)
├── com.ntt:base-web-starter
├── com.ntt:base-data-starter
├── com.ntt:common-log
├── spring-boot-starter-web
├── spring-boot-starter-data-jpa
├── spring-boot-starter-validation
├── spring-boot-starter-actuator
├── spring-boot-starter-mail              ← SMTP
├── io.github.resilience4j:resilience4j-spring-boot3  ← Circuit Breaker
├── org.flywaydb:flyway-core
├── org.flywaydb:flyway-database-postgresql
├── io.micrometer:micrometer-registry-prometheus
├── org.postgresql:postgresql (runtime)
└── com.fasterxml.jackson.module:jackson-module-kotlin

notification-client
├── com.ntt:base-data-starter             ← JPA for entity
├── spring-boot-starter-data-jpa
└── spring-boot-autoconfigure
```

## 6. Build Configuration

### notification-service/build.gradle.kts
- Plugin: `ntt.spring-app-conventions`
- Pattern: Same as account-service (DD-007)
- AOT disabled: `tasks.named("processAot") { enabled = false }`

### notification-service/settings.gradle.kts
- Pattern: Same as account-service
- `rootProject.name = "notification-service"`
- Version catalog: `com.ntt:version-catalog:0.0.1-SNAPSHOT`
- Build logic: `com.ntt.build:build-logic:0.0.1-SNAPSHOT`

### notification-client/build.gradle.kts
- Plugin: `java-library` + `maven-publish`
- Publish: `./gradlew :notification-client:publishToMavenLocal`

## 7. Auth-service Changes

### Files to DELETE
- `auth/application/MailJobScheduler.kt`
- `auth/application/MailQueueService.kt`
- `auth/adapter/out/persistence/entity/MailQueueEntity.kt`
- `auth/adapter/out/persistence/entity/MailStatus.kt`
- `auth/adapter/out/persistence/entity/MailTemplateEntity.kt` (if exists)
- `auth/adapter/out/persistence/repository/MailQueueRepository.kt`
- `auth/adapter/out/persistence/repository/MailTemplateRepository.kt`

### Files to MODIFY
- `auth/application/NewDeviceMailHandler.kt` → use `NotificationPort.enqueue()` instead of `MailQueueService.enqueue()`
- `shared/config/SecurityProperties.kt` → remove `MailProperties` inner class
- `build.gradle.kts` → add `implementation("com.ntt:notification-client:0.0.1-SNAPSHOT")`
- `application.yml` → remove `app.security.mail.*`, add `app.notification.datasource.*`

### New Migration
- `V22__drop_mail_tables.sql` → `DROP TABLE IF EXISTS mail_queue; DROP TABLE IF EXISTS mail_templates;`
