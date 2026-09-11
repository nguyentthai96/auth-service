# Research Brief: Notification Service

## 1. Feature Description

**Tên Feature:** Notification Service (tách riêng từ auth-service)

**Mô tả:** Tách phần notification/outbox (jobs notify outbox, send mail) từ auth-service thành một microservice độc lập, hỗ trợ multi-channel notification (SMS, Email, Firebase Push, OTT...) với kiến trúc extensible, chạy trên PID/process Spring Boot riêng, database riêng, dễ dàng scaling horizontal.

**Yêu cầu chính:**
1. Multi-channel notifier: SMS, Mail (SMTP), Firebase Cloud Messaging, OTT (Zalo, Telegram...)
2. Chạy dưới dạng: Jobs scan table (polling), RESTful API, gRPC, Kafka message pub/sub
3. Port/Adapter pattern cho phép nhúng vào các microservice khác (client SDK insert nhanh vào notification_queue table)
4. Retry mechanism + collection trạng thái failed + report số lượng message thành công
5. Tracking trạng thái user đã view message (read receipts)
6. Revoke/unsend message capability
7. Chạy trên process Spring Boot riêng với database riêng → giảm tải hệ thống chung + dễ scaling

## 2. Input Mode

- **Mode:** Idea (mô tả tự do + context code hiện tại)
- **Source:** User request + existing codebase analysis

## 3. Research Keywords

| # | Keyword | Purpose |
|---|---------|---------|
| 1 | notification microservice architecture pattern | Kiến trúc tổng thể |
| 2 | transactional outbox pattern multi-channel | Pattern chính đang dùng |
| 3 | notification service Spring Boot Kotlin clean architecture | Tech stack |
| 4 | multi-channel notification SMS email push Firebase | Phương thức gửi |
| 5 | notification delivery tracking read receipt revoke unsend | Tracking & lifecycle |
| 6 | Novu open source notification infrastructure | OSS alternative |
| 7 | notification retry strategy exponential backoff dead letter queue | Retry mechanism |
| 8 | notification port adapter SDK client library | Client SDK pattern |
| 9 | notification service database schema design | Schema design |
| 10 | Kafka consumer notification worker fan-out pattern | Message processing |

## 4. Current System Analysis

### 4.1 Related Features (Existing Code in auth-service)

| Component | File | Role |
|-----------|------|------|
| EventOutboxEntity | `adapter/out/persistence/entity/EventOutboxEntity.kt` | Event outbox table (Kafka relay) |
| MailQueueEntity | `adapter/out/persistence/entity/MailQueueEntity.kt` | Mail queue table (SMTP relay) |
| MailTemplateEntity | `adapter/out/persistence/entity/MailTemplateEntity.kt` | Email template storage |
| MailStatus | `adapter/out/persistence/entity/MailStatus.kt` | Enum: PENDING → PROCESSING → SENT/FAILED |
| MailQueueService | `application/MailQueueService.kt` | Enqueue mail (same TX) + template rendering |
| MailJobScheduler | `application/MailJobScheduler.kt` | Poll mail_queue → send SMTP (5s interval) |
| NewDeviceMailHandler | `application/NewDeviceMailHandler.kt` | Handle new device login event → enqueue mail |
| OutboxPoller | `adapter/out/event/OutboxPoller.kt` | Poll event_outbox → publish to Kafka (100ms) |
| OutboxPersistenceAdapter | `adapter/out/persistence/OutboxPersistenceAdapter.kt` | JPA adapter for OutboxPort |
| OutboxPort | `application/port/out/OutboxPort.kt` | Port interface for outbox operations |
| NotificationGateway | `application/port/out/NotificationGateway.kt` | Port interface for send notifications |
| KafkaNotificationGateway | `adapter/out/notification/KafkaNotificationGateway.kt` | Kafka-based notification publishing |
| LoggingNotificationGateway | `adapter/out/notification/LoggingNotificationGateway.kt` | Dev/test logging fallback |
| EventPublisher | `application/port/out/EventPublisher.kt` | Port for domain event publishing |
| KafkaEventPublisher | `adapter/out/event/KafkaEventPublisher.kt` | Kafka domain event publisher |
| EventService | `application/event/EventService.kt` | Event recording (event store + outbox) |
| OutboxProperties | `shared/config/OutboxProperties.kt` | Outbox poller config |
| SecurityProperties.MailProperties | `shared/config/SecurityProperties.kt` | Mail queue config (batch, retry, cleanup) |

### 4.2 Existing Patterns

| Pattern | Implementation | Details |
|---------|---------------|---------|
| **Transactional Outbox** | EventOutboxEntity + OutboxPoller | Event outbox cho Kafka relay (SELECT FOR UPDATE SKIP LOCKED) |
| **Mail Queue (Outbox variant)** | MailQueueEntity + MailJobScheduler | Riêng cho SMTP mail, poll 5s, retry exponential backoff |
| **Clean Architecture** | Port/Adapter layers | Domain → Application (ports) → Adapter (implementations) |
| **Template Rendering** | MailTemplateEntity + MailQueueService | {{key}} placeholder substitution |
| **Provider Toggle** | @ConditionalOnProperty | Kafka vs Logging gateway switching |
| **Retry Strategy** | Exponential backoff | base × 2^retryCount, max retries configurable |
| **Pessimistic Locking** | FOR UPDATE SKIP LOCKED | Multi-instance safe polling |
| **Snowflake ID** | SnowflakePersistentAuditableEntity | Base class for all entities |

### 4.3 Tech Stack Constraints

| Technology | Version/Details |
|-----------|----------------|
| Language | Kotlin |
| Framework | Spring Boot (convention plugin `ntt.spring-app-conventions`) |
| Build | Gradle Kotlin DSL |
| Database | PostgreSQL (Flyway migrations) |
| Messaging | Kafka (spring-kafka) |
| Cache | Redis (spring-boot-starter-data-redis) |
| Observability | Micrometer + Actuator |
| Base Library | `com.ntt:platform:0.0.1-SNAPSHOT` (base-core starters) |
| ID Strategy | Snowflake (SnowflakePersistentAuditableEntity from base-core) |
| Config | `@ConfigurationProperties` + application.yml |

### 4.4 Integration Points

| Integration | Direction | Protocol | Details |
|------------|-----------|----------|---------|
| auth-service → notification-service | Produce | Kafka / Direct DB insert | Auth events trigger notifications |
| notification-service → SMTP | Outbound | SMTP (JavaMailSender) | Email delivery |
| notification-service → Kafka | Outbound | Kafka producer | Event relay |
| account-service (future) | Produce | Kafka / Direct DB insert | Profile events → notifications |
| system-admin-service (future) | Produce | Kafka / Direct DB insert | Admin alerts → notifications |

### 4.5 Current Callers of Mail Enqueue

| Caller | File | Trigger |
|--------|------|---------|
| NewDeviceMailHandler | `application/NewDeviceMailHandler.kt` | `@TransactionalEventListener(AFTER_COMMIT)` for `NewDeviceLoginEvent` |
| (Test) | `MailQueueServiceTest.kt` | Unit test |

### 4.6 Database Schema (Existing Tables to Migrate)

**event_outbox** (V11 migration):
- id, aggregate_type, aggregate_id, event_type, topic, partition_key, payload (JSONB), status, retry_count, created_at, published_at

**mail_queue** (V16 migration):
- id, recipient, subject, template_code, template_data (JSONB), body_rendered, status, retry_count, max_retries, error_message, sent_at, next_retry_at, created_by, created_at, updated_at

**mail_templates** (entity-based):
- id, code, name, subject_template, body_template, language

## 5. Phạm vi Research

### In Scope
- Architecture design cho notification-service standalone
- Multi-channel notification pattern (Strategy/Factory pattern)
- Client SDK/Port adapter cho producer services
- Database schema thiết kế mới (notification-specific DB)
- Retry + Dead Letter Queue + Status tracking
- Read receipt + Revoke/Unsend mechanism
- Migration strategy từ auth-service

### Out of Scope
- UI/Dashboard cho quản lý notifications
- Real-time WebSocket notification (future phase)
- Analytics/BI reporting (future phase)
