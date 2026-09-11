# Software Requirements Specification: notification-service

## 1. Introduction

### 1.1 Purpose
Tài liệu SRS cho notification-service — microservice độc lập xử lý async notification delivery qua multi-channel (Email, SMS, Push, OTT).

### 1.2 Scope
Phase 1: Email channel + Transactional Outbox + notification-client SDK.

### 1.3 Definitions

| Term | Definition |
|------|-----------|
| Transactional Outbox | Pattern đảm bảo notification request được persist atomic với business transaction |
| SKIP LOCKED | PostgreSQL locking mode cho phép concurrent consumers skip đang-locked rows |
| Channel | Phương thức delivery: EMAIL, SMS, PUSH, OTT |
| Producer Service | Service gửi notification request (auth-service, account-service...) |
| correlation_id | Unique identifier ngăn duplicate notification |

## 2. Functional Requirements

### 2.1 Enqueue Module

#### FR-001: Enqueue notification trong business transaction
- **Input**: NotificationRequest (recipient, templateCode, templateData, channel, correlationId, priority, sourceService)
- **Processing**: INSERT vào notification_queue trong cùng @Transactional
- **Output**: NotificationQueueEntity (id, status=PENDING)
- **Error**: DB error → rollback business transaction

#### FR-002: Ngăn chặn duplicate notification
- **Input**: correlation_id
- **Processing**: UNIQUE constraint trên correlation_id column
- **Output**: Reject nếu duplicate
- **Error**: `NOTIFICATION_DUPLICATE` (errorCode: "NOTIF-002", HTTP 409)

#### FR-020: Client SDK shared library
- **Input**: notification-client JAR dependency
- **Processing**: Auto-configure NotificationPort bean + secondary datasource
- **Output**: Producer inject NotificationPort, gọi enqueue()
- **Config**: `app.notification.datasource.*` properties

#### FR-022: Notification request validation
- **Input**: NotificationRequest DTO
- **Processing**: Validate: recipient not blank, channel valid enum, templateCode not blank
- **Output**: 400 Bad Request với ProblemDetail nếu invalid
- **Error**: `NOTIFICATION_INVALID_REQUEST` (errorCode: "NOTIF-022", HTTP 400)

### 2.2 Processing Module

#### FR-003: Poll và process notification queue
- **Input**: Scheduler tick (fixedDelay = app.notification.queue.poll-interval-ms, default 5000)
- **Processing**: `SELECT * FROM notification_queue WHERE status='PENDING' AND (next_retry_at IS NULL OR next_retry_at <= now()) ORDER BY priority DESC, created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED`
- **Output**: List<NotificationQueueEntity> for processing
- **Concurrency**: Multi-instance safe via SKIP LOCKED

#### FR-004: Render template trước khi gửi
- **Input**: template_code + template_data (JSONB)
- **Processing**: Lookup NotificationTemplateEntity by code+active, substitute `{{key}}` placeholders
- **Output**: Rendered subject + body
- **Error**: Template not found → status=FAILED, errorMessage="Template not found: {code}", error code `NOTIFICATION_TEMPLATE_NOT_FOUND` ("NOTIF-004")

#### FR-005: Route notification tới channel worker
- **Input**: NotificationQueueEntity with channel field
- **Processing**: NotificationDispatcher.dispatch() → find NotificationSender by channel → sender.send()
- **Output**: Delegated to channel-specific sender
- **Error**: Channel not supported → status=FAILED, errorMessage="Channel not supported: {channel}", error code `NOTIFICATION_CHANNEL_NOT_SUPPORTED` ("NOTIF-005")

#### FR-021: Idempotent notification processing
- **Input**: NotificationQueueEntity with status=PENDING
- **Processing**: Status transition PENDING→PROCESSING (within SELECT FOR UPDATE lock)
- **Output**: Only 1 instance processes each notification
- **Guarantee**: Crash recovery → PROCESSING records auto-revert to PENDING after timeout

### 2.3 Delivery Module

#### FR-006: Gửi email qua SMTP
- **Input**: NotificationQueueEntity (channel=EMAIL, rendered subject+body, recipient)
- **Processing**: JavaMailSender.send(MimeMessage)
- **Output**: status=SENT, sentAt=now()
- **Error**: SMTP error → retry flow (FR-007)

#### FR-007: Retry exponential backoff
- **Input**: Provider error (transient)
- **Processing**: next_retry_at = now() + baseDelaySeconds × 2^retryCount
- **Output**: retryCount++, status=PENDING, next_retry_at calculated
- **Config**: app.notification.queue.base-retry-delay-seconds (default: 30)

#### FR-008: Max retries configurable per channel
- **Input**: retryCount >= maxRetries
- **Processing**: Set status=FAILED
- **Output**: Record không bị pick lại bởi scheduler
- **Config**: app.notification.queue.max-retries (default: 3)

#### FR-009: Failed notification không block queue
- **Input**: SKIP LOCKED query
- **Processing**: FAILED/PROCESSING records bị skip
- **Output**: Other PENDING records tiếp tục được process

#### FR-010: Provider failure không break business flow
- **Input**: Provider error
- **Processing**: Notification retry, business operation unaffected (async)
- **Output**: At-most retry delay, never business TX failure

#### FR-024: Circuit breaker cho external providers
- **Input**: Consecutive failures to provider
- **Processing**: Resilience4j CircuitBreaker per channel
- **Config**:
  - failureRateThreshold: 50%
  - slidingWindowSize: 10
  - waitDurationInOpenState: 30s
  - permittedNumberOfCallsInHalfOpenState: 3
- **Output**: Circuit OPEN → skip provider calls → notifications go to retry

### 2.4 Status Lifecycle Module

#### FR-013: Status lifecycle enforcement
- **Valid transitions**:
  - PENDING → PROCESSING (scheduler picks up)
  - PROCESSING → SENT (delivery success)
  - PROCESSING → FAILED (delivery error after max retries)
  - PROCESSING → PENDING (retry with backoff)
  - SENT → DELIVERED (webhook callback)
  - DELIVERED → READ (read receipt)
  - PENDING → CANCELLED (pre-send cancel)
  - SENT/DELIVERED/READ → REVOKED (logical revoke)
- **Terminal states**: FAILED, REVOKED, CANCELLED
- **Error**: Invalid transition → reject with `NOTIFICATION_INVALID_STATUS_TRANSITION` ("NOTIF-013")

#### FR-014: Revoke notification chưa gửi
- **Input**: POST /api/v1/notifications/{id}/revoke (reason)
- **Processing**: PENDING/PROCESSING → status=CANCELLED
- **Output**: 200 OK
- **Error**: Already terminal → 409 Conflict

#### FR-015: Mark sent notification as REVOKED
- **Input**: POST /api/v1/notifications/{id}/revoke (reason)
- **Processing**: SENT/DELIVERED/READ → status=REVOKED, revokedAt=now(), revokeReason=reason
- **Output**: 200 OK (logical revoke — email/SMS already sent)

#### FR-019: Manual retry failed notification
- **Input**: POST /api/v1/notifications/{id}/retry
- **Processing**: FAILED → reset retryCount=0, status=PENDING, clear errorMessage
- **Output**: 200 OK
- **Error**: Not FAILED → 409 Conflict

### 2.5 Channel Configuration Module

#### FR-023: Channel toggle configuration
- **Config**: app.notification.channels.email.enabled=true (per channel)
- **Processing**: Channel disabled → notifications skip (status=CANCELLED)
- **Output**: No provider call for disabled channels

### 2.6 Observability Module

#### FR-017: Export metrics qua Micrometer
- **Counters**:
  - `notification.send{channel, result=success|failed|retry|template_not_found}`
  - `notification.enqueue{source_service, channel}`
  - `notification.revoke{channel}`
- **Endpoint**: `/actuator/prometheus`

#### FR-018: Cleanup old records
- **Schedule**: Cron `0 0 2 * * *` (daily 2AM)
- **Processing**: DELETE FROM notification_queue WHERE status IN ('SENT','DELIVERED','READ') AND sent_at < (now - retentionDays)
- **Config**: app.notification.queue.cleanup-after-days (default: 30)

### 2.7 Tracking Module (Phase 3 — Design Only)

#### FR-011: Track delivery status via webhook
- **Endpoint**: POST /api/v1/webhooks/delivery
- **Input**: Provider callback (messageId, status, timestamp)
- **Processing**: Update status=DELIVERED, deliveredAt=now()

#### FR-012: Track read receipt
- **Methods**: Pixel tracking (GET /api/v1/track/{id}/pixel) + Link click (GET /api/v1/track/{id}/click)
- **Config**: notification_template.tracking_mode (PIXEL|LINK|BOTH|NONE, default: NONE)

#### FR-016: Push notification collapse on revoke
- **Processing**: FCM update with collapse_key → replace on device (Phase 2+)

## 3. Non-functional Requirements

| ID | Category | Requirement | Target |
|----|----------|-------------|--------|
| NFR-001 | Performance | Enqueue API response time | < 50ms p99 |
| NFR-002 | Throughput | Notification processing | ≥ 100 msg/s (single instance, Phase 1) |
| NFR-003 | Isolation | Separate database | notification-db (PostgreSQL) |
| NFR-004 | Scalability | Horizontal scaling | Multi-instance via SKIP LOCKED |
| NFR-005 | Reliability | Zero message loss | Transactional Outbox (at-least-once) |
| NFR-006 | Stack | Technology | Spring Boot 3.x + Kotlin + base-core conventions |
| NFR-007 | ID | ID generation | Snowflake ID (SnowflakePersistentAuditableEntity) |

## 4. Error Codes

| Code | Name | HTTP | Description |
|------|------|------|-------------|
| NOTIF-001 | NOTIFICATION_ENQUEUE_FAILED | 500 | Failed to enqueue notification |
| NOTIF-002 | NOTIFICATION_DUPLICATE | 409 | Duplicate correlation_id |
| NOTIF-004 | NOTIFICATION_TEMPLATE_NOT_FOUND | 404 | Template code not found or inactive |
| NOTIF-005 | NOTIFICATION_CHANNEL_NOT_SUPPORTED | 400 | Channel type not supported |
| NOTIF-013 | NOTIFICATION_INVALID_STATUS_TRANSITION | 409 | Invalid status transition |
| NOTIF-014 | NOTIFICATION_NOT_FOUND | 404 | Notification ID not found |
| NOTIF-022 | NOTIFICATION_INVALID_REQUEST | 400 | Invalid notification request |

## 5. API Endpoints

| Method | Path | Description | FR |
|--------|------|-------------|-----|
| POST | /api/v1/notifications | Enqueue notification (REST) | FR-001 |
| GET | /api/v1/notifications/{id} | Get notification status | FR-013 |
| POST | /api/v1/notifications/{id}/retry | Manual retry failed | FR-019 |
| POST | /api/v1/notifications/{id}/revoke | Revoke/cancel notification | FR-014, FR-015 |
| GET | /api/v1/notifications | List notifications (filtered) | — |
| POST | /api/v1/webhooks/delivery | Delivery status callback | FR-011 |
| GET | /api/v1/track/{id}/pixel | Pixel tracking (1x1 image) | FR-012 |
| GET | /api/v1/track/{id}/click | Link click tracking (302) | FR-012 |

## 6. Database Schema

### notification_queue

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGINT PK | NO | Snowflake | Primary key |
| correlation_id | VARCHAR(100) UNIQUE | YES | — | Dedup key |
| channel | VARCHAR(20) | NO | — | EMAIL/SMS/PUSH/OTT |
| priority | VARCHAR(10) | NO | 'NORMAL' | LOW/NORMAL/HIGH/URGENT |
| recipient | VARCHAR(255) | NO | — | Target address |
| subject | VARCHAR(500) | YES | — | Rendered subject |
| template_code | VARCHAR(100) | NO | — | Template reference |
| template_data | JSONB | NO | '{}' | Placeholder values |
| body_rendered | TEXT | YES | — | Rendered body |
| status | VARCHAR(20) | NO | 'PENDING' | Current status |
| retry_count | INT | NO | 0 | Current retry count |
| max_retries | INT | NO | 3 | Max allowed retries |
| error_message | TEXT | YES | — | Last error message |
| sent_at | TIMESTAMP | YES | — | When sent |
| delivered_at | TIMESTAMP | YES | — | When delivered |
| read_at | TIMESTAMP | YES | — | When read |
| revoked_at | TIMESTAMP | YES | — | When revoked |
| revoke_reason | VARCHAR(500) | YES | — | Revoke reason |
| next_retry_at | TIMESTAMP | YES | — | Next retry time |
| source_service | VARCHAR(100) | YES | — | Producer service name |
| created_by | BIGINT | YES | — | User who triggered |
| created_at | TIMESTAMP | NO | NOW() | Record creation |
| updated_at | TIMESTAMP | NO | NOW() | Last update |

### notification_template

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGINT PK | NO | Snowflake | Primary key |
| code | VARCHAR(100) UNIQUE | NO | — | Template code |
| name | VARCHAR(255) | NO | — | Template name |
| channel | VARCHAR(20) | NO | 'EMAIL' | Target channel |
| subject_template | VARCHAR(500) | NO | — | Subject with {{key}} |
| body_template | TEXT | NO | — | Body with {{key}} |
| tracking_mode | VARCHAR(10) | NO | 'NONE' | PIXEL/LINK/BOTH/NONE |
| language | VARCHAR(10) | NO | 'vi' | Language code |
| active | BOOLEAN | NO | TRUE | Is active |
| created_at | TIMESTAMP | NO | NOW() | Record creation |
| updated_at | TIMESTAMP | NO | NOW() | Last update |
