# Technical Specification: Notification Service

## 1. Architecture Overview

### 1.1 System Context

```mermaid
C4Context
    title System Context - Notification Service
    
    Person(user, "End User", "Receives notifications")
    
    System_Boundary(bigbang, "BigBang Platform") {
        System(auth, "auth-service", "Authentication & Authorization")
        System(account, "account-service", "User profiles & settings")
        System(admin, "system-admin-service", "Admin operations")
        System(notify, "notification-service", "Multi-channel notification delivery")
    }
    
    System_Ext(smtp, "SMTP Provider", "Email delivery")
    System_Ext(sms, "SMS Gateway", "SMS delivery")
    System_Ext(fcm, "Firebase FCM", "Push notifications")
    System_Ext(ott, "OTT APIs", "Zalo, Telegram, etc.")
    
    Rel(auth, notify, "Enqueue notification", "Kafka / Direct Insert")
    Rel(account, notify, "Enqueue notification", "Kafka / Direct Insert")
    Rel(admin, notify, "Enqueue notification", "Kafka / Direct Insert")
    Rel(notify, smtp, "Send email", "SMTP")
    Rel(notify, sms, "Send SMS", "REST API")
    Rel(notify, fcm, "Send push", "FCM SDK")
    Rel(notify, ott, "Send OTT", "REST API")
    Rel(notify, user, "Deliver notification")
```

### 1.2 Container Diagram

```mermaid
C4Container
    title Container - Notification Service
    
    Container(api, "REST API", "Spring Boot", "RESTful endpoints for enqueue, revoke, status")
    Container(kafka_consumer, "Kafka Consumer", "Spring Kafka", "Consume notification events from Kafka")
    Container(dispatcher, "Notification Dispatcher", "Scheduled Job", "Poll queue, route to channel workers")
    Container(email_worker, "Email Worker", "SmtpEmailSender", "SMTP mail delivery")
    Container(sms_worker, "SMS Worker", "SmsSender", "SMS gateway integration")
    Container(push_worker, "Push Worker", "FcmPushSender", "Firebase push delivery")
    Container(webhook, "Webhook Listener", "REST Controller", "Receive delivery/read callbacks")
    ContainerDb(db, "notification-db", "PostgreSQL", "notification_queue, templates, preferences")
    ContainerDb(redis, "Redis", "Redis", "Deduplication cache, rate limiting")
    
    Rel(api, db, "Insert notification")
    Rel(kafka_consumer, db, "Insert notification")
    Rel(dispatcher, db, "Poll & process")
    Rel(dispatcher, email_worker, "Route email")
    Rel(dispatcher, sms_worker, "Route SMS")
    Rel(dispatcher, push_worker, "Route push")
    Rel(webhook, db, "Update status")
```

## 2. Clean Architecture Layers

```
notification-service/
├── domain/                          # Domain layer (pure Kotlin)
│   ├── model/
│   │   ├── NotificationChannel.kt   # Enum: EMAIL, SMS, PUSH, OTT
│   │   ├── NotificationStatus.kt    # Enum: PENDING → ... → READ
│   │   ├── NotificationPriority.kt  # Enum: LOW, NORMAL, HIGH, URGENT
│   │   └── NotificationRequest.kt   # Value object for notification creation
│   └── event/
│       └── NotificationEvent.kt     # Domain events
│
├── application/                     # Application layer (use cases)
│   ├── port/
│   │   ├── in/
│   │   │   ├── EnqueueNotificationUseCase.kt    # UC-001
│   │   │   ├── RevokeNotificationUseCase.kt     # UC-004
│   │   │   └── QueryNotificationUseCase.kt      # UC-003, UC-005
│   │   └── out/
│   │       ├── NotificationQueuePort.kt          # DB operations
│   │       ├── NotificationSender.kt             # Strategy interface
│   │       ├── TemplatePort.kt                   # Template rendering
│   │       └── DeliveryWebhookPort.kt            # Webhook processing
│   ├── service/
│   │   ├── NotificationEnqueueService.kt         # Enqueue logic
│   │   ├── NotificationDispatcherService.kt      # Poll + route
│   │   ├── NotificationRevokeService.kt          # Revoke logic
│   │   └── TemplateRenderService.kt              # Template rendering
│   └── scheduler/
│       ├── NotificationJobScheduler.kt            # @Scheduled polling
│       └── NotificationCleanupScheduler.kt        # Cleanup old records
│
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   ├── NotificationController.kt          # REST API
│   │   │   └── WebhookController.kt               # Provider callbacks
│   │   └── kafka/
│   │       └── NotificationKafkaConsumer.kt        # Kafka consumer
│   ├── out/
│   │   ├── persistence/
│   │   │   ├── entity/
│   │   │   │   ├── NotificationQueueEntity.kt     # Unified queue table
│   │   │   │   ├── NotificationTemplateEntity.kt  # Templates
│   │   │   │   └── NotificationPreferenceEntity.kt # User preferences
│   │   │   ├── repository/
│   │   │   │   ├── NotificationQueueRepository.kt
│   │   │   │   ├── NotificationTemplateRepository.kt
│   │   │   │   └── NotificationPreferenceRepository.kt
│   │   │   └── NotificationPersistenceAdapter.kt
│   │   └── sender/                                 # Channel implementations
│   │       ├── SmtpEmailSender.kt
│   │       ├── TwilioSmsSender.kt
│   │       ├── FcmPushSender.kt
│   │       └── ZaloOttSender.kt
│   └── config/
│       └── NotificationProperties.kt
│
└── shared/
    └── config/
        └── NotificationServiceProperties.kt
```

## 3. Database Schema (notification-db)

### 3.1 ERD Diagram

```mermaid
erDiagram
    NOTIFICATION_QUEUE {
        bigint id PK "Snowflake ID"
        varchar(100) channel "EMAIL, SMS, PUSH, OTT"
        varchar(20) priority "LOW, NORMAL, HIGH, URGENT"
        bigint user_id "Target user ID"
        varchar(255) recipient "Email/phone/device_token"
        varchar(100) template_code FK "Template reference"
        jsonb template_data "Placeholder values"
        varchar(500) subject "Rendered subject"
        text body_rendered "Rendered body"
        varchar(20) status "PENDING, PROCESSING, SENT, DELIVERED, READ, FAILED, REVOKED, CANCELLED"
        int retry_count "Current retry count"
        int max_retries "Max allowed retries"
        text error_message "Last error"
        varchar(100) correlation_id UK "Idempotency key"
        varchar(100) source_service "Producer service name"
        varchar(100) source_event_type "Triggering event type"
        timestamp sent_at "When sent to provider"
        timestamp delivered_at "When provider confirmed delivery"
        timestamp read_at "When user read/viewed"
        timestamp revoked_at "When revoked"
        varchar(255) revoke_reason "Revoke reason"
        timestamp next_retry_at "Next retry time"
        timestamp expires_at "TTL expiration"
        bigint created_by "User who triggered"
        timestamp created_at
        timestamp updated_at
    }
    
    NOTIFICATION_TEMPLATE {
        bigint id PK "Snowflake ID"
        varchar(100) code UK "Template code"
        varchar(255) name "Display name"
        varchar(100) channel "EMAIL, SMS, PUSH, OTT"
        varchar(500) subject_template "Subject with placeholders"
        text body_template "Body with placeholders"
        varchar(10) language "vi, en"
        boolean active "Is template active"
        timestamp created_at
        timestamp updated_at
    }
    
    NOTIFICATION_PREFERENCE {
        bigint id PK "Snowflake ID"
        bigint user_id "User ID"
        varchar(100) notification_type "e.g., NEW_DEVICE_LOGIN"
        varchar(100) channel "EMAIL, SMS, PUSH"
        boolean enabled "User opt-in/out"
        timestamp created_at
        timestamp updated_at
    }
    
    NOTIFICATION_DEAD_LETTER {
        bigint id PK "Snowflake ID"
        bigint original_id "Original notification_queue ID"
        varchar(100) channel
        varchar(255) recipient
        jsonb original_payload "Full original request"
        text failure_reason "Why it failed permanently"
        int total_retries
        timestamp failed_at
        timestamp created_at
    }
    
    NOTIFICATION_QUEUE ||--o{ NOTIFICATION_TEMPLATE : "references"
    NOTIFICATION_QUEUE ||--o| NOTIFICATION_DEAD_LETTER : "moves to on max retry"
    NOTIFICATION_PREFERENCE }o--|| NOTIFICATION_QUEUE : "filters"
```

### 3.2 DDL (Flyway Migration V1)

```sql
-- V1__notification_service_init.sql

-- 1. Unified notification queue
CREATE TABLE notification_queue (
    id                  BIGINT PRIMARY KEY,
    channel             VARCHAR(100) NOT NULL,
    priority            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    user_id             BIGINT,
    recipient           VARCHAR(255) NOT NULL,
    template_code       VARCHAR(100) NOT NULL,
    template_data       JSONB NOT NULL DEFAULT '{}',
    subject             VARCHAR(500),
    body_rendered       TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count         INT NOT NULL DEFAULT 0,
    max_retries         INT NOT NULL DEFAULT 3,
    error_message       TEXT,
    correlation_id      VARCHAR(100) NOT NULL,
    source_service      VARCHAR(100) NOT NULL,
    source_event_type   VARCHAR(100),
    sent_at             TIMESTAMP,
    delivered_at        TIMESTAMP,
    read_at             TIMESTAMP,
    revoked_at          TIMESTAMP,
    revoke_reason       VARCHAR(255),
    next_retry_at       TIMESTAMP,
    expires_at          TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_notification_correlation ON notification_queue(correlation_id);
CREATE INDEX idx_notification_status_retry ON notification_queue(status, next_retry_at);
CREATE INDEX idx_notification_channel_status ON notification_queue(channel, status);
CREATE INDEX idx_notification_user ON notification_queue(user_id, created_at DESC);
CREATE INDEX idx_notification_created ON notification_queue(created_at);

-- 2. Notification templates
CREATE TABLE notification_template (
    id                  BIGINT PRIMARY KEY,
    code                VARCHAR(100) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    channel             VARCHAR(100) NOT NULL DEFAULT 'EMAIL',
    subject_template    VARCHAR(500),
    body_template       TEXT NOT NULL,
    language            VARCHAR(10) NOT NULL DEFAULT 'vi',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 3. User notification preferences
CREATE TABLE notification_preference (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    notification_type   VARCHAR(100) NOT NULL,
    channel             VARCHAR(100) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_pref_user_type_channel 
    ON notification_preference(user_id, notification_type, channel);

-- 4. Dead letter queue
CREATE TABLE notification_dead_letter (
    id                  BIGINT PRIMARY KEY,
    original_id         BIGINT NOT NULL,
    channel             VARCHAR(100) NOT NULL,
    recipient           VARCHAR(255) NOT NULL,
    original_payload    JSONB NOT NULL,
    failure_reason      TEXT,
    total_retries       INT NOT NULL DEFAULT 0,
    failed_at           TIMESTAMP NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dead_letter_created ON notification_dead_letter(created_at);
```

## 4. Key Interfaces (Domain Ports)

### 4.1 NotificationSender — Strategy Pattern (Core Extension Point)

```kotlin
/**
 * Strategy interface for multi-channel notification delivery.
 * Each channel implements this interface. New channels are added
 * by creating a new implementation — no dispatcher changes needed.
 */
interface NotificationSender {
    /** Which channel this sender handles. */
    fun channel(): NotificationChannel
    
    /** Send notification to recipient. Returns delivery result. */
    fun send(request: SendRequest): SendResult
}

data class SendRequest(
    val notificationId: Long,
    val recipient: String,
    val subject: String?,
    val body: String,
    val channel: NotificationChannel,
    val metadata: Map<String, String> = emptyMap()
)

sealed class SendResult {
    data class Success(val providerMessageId: String? = null) : SendResult()
    data class Retry(val reason: String, val delaySeconds: Long? = null) : SendResult()
    data class Failed(val reason: String, val permanent: Boolean = false) : SendResult()
}
```

### 4.2 NotificationPort — Client SDK Interface

```kotlin
/**
 * Port interface for producer services (notification-client library).
 * Implementations: DirectInsertAdapter, KafkaAdapter, RestAdapter.
 */
interface NotificationPort {
    fun enqueue(request: NotificationRequest): Long // returns notification ID
}

data class NotificationRequest(
    val channel: NotificationChannel,
    val recipient: String,
    val templateCode: String,
    val templateData: Map<String, Any> = emptyMap(),
    val userId: Long? = null,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val correlationId: String = UUID.randomUUID().toString(),
    val sourceService: String,
    val sourceEventType: String? = null,
    val expiresAt: Instant? = null,
    val createdBy: Long? = null
)
```

## 5. Sequence Diagrams

### 5.1 Enqueue → Deliver (Happy Path)

```mermaid
sequenceDiagram
    participant AS as auth-service
    participant NP as NotificationPort<br/>(client SDK)
    participant DB as notification-db
    participant NJS as NotificationJobScheduler
    participant ND as NotificationDispatcher
    participant ES as SmtpEmailSender
    participant SMTP as SMTP Provider
    
    AS->>AS: Business event occurs<br/>(NewDeviceLogin)
    AS->>NP: enqueue(NotificationRequest)
    NP->>DB: INSERT notification_queue<br/>(status=PENDING)
    Note over AS,DB: Same @Transactional as business operation
    
    loop Every 5 seconds
        NJS->>DB: SELECT ... FOR UPDATE SKIP LOCKED<br/>(status=PENDING, next_retry_at <= now)
        DB-->>NJS: List<NotificationQueueEntity>
        NJS->>ND: dispatch(notification)
        ND->>ND: Resolve channel (EMAIL)
        ND->>ES: send(SendRequest)
        ES->>SMTP: Send email
        SMTP-->>ES: OK
        ES-->>ND: SendResult.Success
        ND->>DB: UPDATE status=SENT, sent_at=now()
    end
```

### 5.2 Retry Flow

```mermaid
sequenceDiagram
    participant NJS as NotificationJobScheduler
    participant ND as NotificationDispatcher
    participant Sender as ChannelSender
    participant Provider as External Provider
    participant DB as notification-db
    participant DLQ as Dead Letter
    
    NJS->>DB: Poll PENDING notifications
    NJS->>ND: dispatch(notification)
    ND->>Sender: send(request)
    Sender->>Provider: Call provider API
    Provider-->>Sender: Error 503 (transient)
    Sender-->>ND: SendResult.Retry(reason)
    
    ND->>DB: retry_count++<br/>next_retry_at = now + base * 2^retry<br/>status = PENDING
    
    Note over NJS: Next poll cycle picks up again
    
    alt retry_count >= max_retries
        ND->>DB: status = FAILED
        ND->>DLQ: INSERT dead_letter record
    end
```

### 5.3 Revoke Flow

```mermaid
sequenceDiagram
    participant Client as Admin/User
    participant API as NotificationController
    participant SVC as RevokeService
    participant DB as notification-db
    
    Client->>API: POST /api/notifications/{id}/revoke
    API->>SVC: revoke(id, reason)
    SVC->>DB: SELECT notification WHERE id=?
    
    alt status = PENDING/PROCESSING
        SVC->>DB: UPDATE status=CANCELLED
    else status = SENT/DELIVERED
        SVC->>DB: UPDATE status=REVOKED, revoked_at=now()
    else status = FAILED/REVOKED
        SVC-->>API: No action needed
    end
    
    API-->>Client: 200 OK
```

## 6. API Endpoints

### 6.1 REST API

| Method | Endpoint | Description | Priority |
|--------|----------|-------------|----------|
| POST | `/api/v1/notifications` | Enqueue notification | P0 |
| POST | `/api/v1/notifications/batch` | Batch enqueue | P1 |
| GET | `/api/v1/notifications/{id}` | Get notification status | P1 |
| GET | `/api/v1/notifications` | Query notifications (filter by user, status, channel) | P1 |
| POST | `/api/v1/notifications/{id}/revoke` | Revoke/unsend notification | P2 |
| POST | `/api/v1/notifications/{id}/retry` | Manual retry failed notification | P2 |
| POST | `/api/v1/webhooks/delivery` | Provider delivery callback | P1 |
| POST | `/api/v1/webhooks/read` | Provider read callback | P2 |
| GET | `/api/v1/notifications/report` | Delivery statistics report | P2 |

### 6.2 Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `notification.enqueue` | Consumer | Receive notification requests from producers |
| `notification.status` | Producer | Emit status change events |
| `notification.dead-letter` | Producer | Emit permanently failed notifications |

## 7. Configuration

```yaml
# application.yml for notification-service
app:
  notification:
    queue:
      poll-interval-ms: 5000
      batch-size: 50
      max-retries: 3
      base-retry-delay-seconds: 30
      cleanup-after-days: 30
    channels:
      email:
        enabled: true
        provider: smtp  # smtp | sendgrid | ses
      sms:
        enabled: false
        provider: twilio  # twilio | nexmo
      push:
        enabled: false
        provider: fcm  # fcm | apns
      ott:
        enabled: false
        providers:
          zalo:
            enabled: false
          telegram:
            enabled: false

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification_db  # Separate DB!
```

## 8. Client SDK (notification-client)

### 8.1 Module Structure

```
notification-client/  (shared library, published to Maven local)
├── build.gradle.kts
├── src/main/kotlin/com/ntt/notification/client/
│   ├── NotificationPort.kt              # Port interface
│   ├── NotificationRequest.kt           # Request DTO
│   ├── NotificationChannel.kt           # Channel enum
│   ├── NotificationPriority.kt          # Priority enum
│   ├── adapter/
│   │   ├── DirectInsertNotificationAdapter.kt  # Same-DB insert
│   │   ├── KafkaNotificationAdapter.kt          # Kafka publish
│   │   └── RestNotificationAdapter.kt           # REST API call
│   └── config/
│       └── NotificationClientAutoConfiguration.kt  # Spring Boot auto-config
```

### 8.2 Usage in auth-service (After Migration)

```kotlin
// auth-service build.gradle.kts
dependencies {
    implementation("com.ntt:notification-client:0.0.1-SNAPSHOT")
}

// NewDeviceMailHandler.kt (after migration)
@Component
class NewDeviceMailHandler(
    private val notificationPort: NotificationPort  // Injected via auto-config
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleNewDeviceLogin(event: NewDeviceLoginEvent) {
        notificationPort.enqueue(
            NotificationRequest(
                channel = NotificationChannel.EMAIL,
                recipient = event.email ?: return,
                templateCode = "NEW_DEVICE_LOGIN",
                templateData = mapOf(
                    "userName" to event.username,
                    "deviceName" to event.deviceName,
                    // ... other fields
                ),
                userId = event.userId,
                sourceService = "auth-service",
                sourceEventType = "NEW_DEVICE_LOGIN"
            )
        )
    }
}
```

## 9. Implementation Notes (Agent Guidelines)

### 9.1 Files to Migrate from auth-service

| Source (auth-service) | Target (notification-service) | Action |
|----------------------|-------------------------------|--------|
| MailQueueEntity | NotificationQueueEntity | Refactor + extend |
| MailTemplateEntity | NotificationTemplateEntity | Refactor |
| MailStatus | NotificationStatus | Extend (add DELIVERED, READ, REVOKED, CANCELLED) |
| MailQueueService | NotificationEnqueueService | Refactor |
| MailJobScheduler | NotificationJobScheduler | Refactor + Strategy dispatch |
| MailQueueRepository | NotificationQueueRepository | Refactor |
| MailTemplateRepository | NotificationTemplateRepository | Refactor |
| SecurityProperties.MailProperties | NotificationProperties | Extract + extend |

### 9.2 Files to Remove from auth-service (After Migration)

| File | Replacement |
|------|-------------|
| MailQueueEntity.kt | notification-client SDK |
| MailTemplateEntity.kt | In notification-service |
| MailStatus.kt | In notification-service |
| MailQueueService.kt | NotificationPort from client SDK |
| MailJobScheduler.kt | In notification-service |
| MailQueueRepository.kt | In notification-service |
| MailTemplateRepository.kt | In notification-service |
| V16__create_mail_queue.sql | In notification-service migrations |

### 9.3 Files to Keep in auth-service (NOT migrated)

| File | Reason |
|------|--------|
| EventOutboxEntity.kt | Used for domain event outbox (non-notification) |
| OutboxPoller.kt | Kafka event relay (non-notification) |
| EventService.kt | Event sourcing (non-notification) |
| NewDeviceMailHandler.kt | Remains, but uses NotificationPort |
| KafkaNotificationGateway.kt | Can be replaced by NotificationPort |
| NotificationGateway.kt | Replaced by NotificationPort |

### 9.4 Extension Pattern

Adding a new channel (e.g., Telegram):
1. Create `TelegramOttSender` implementing `NotificationSender`
2. Return `NotificationChannel.OTT` from `channel()`
3. Register as `@Component`
4. Done — dispatcher auto-discovers via Spring DI

No changes to dispatcher, controller, or scheduler code.
