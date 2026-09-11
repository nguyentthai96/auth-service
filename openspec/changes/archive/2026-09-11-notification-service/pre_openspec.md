# Pre-OpenSpec: notification-service

> **Type**: NEWBUILD
> **Flow**: Command
> **Source**: URD (local file — business_analysis.md + technical_spec.md from research phase)
> **Classification Evidence**: keyword "notification-service" → no existing service directory at `services/` → NEWBUILD
> **Archive**: N/A
> **Quality Score**: 82/100

## 📋 Feature Summary

Tách notification infrastructure (outbox + mail sender) từ auth-service thành microservice độc lập — **notification-service**. Service này chạy trên PID/process Spring Boot riêng, database PostgreSQL riêng, hỗ trợ multi-channel delivery (Email, SMS, Push, OTT) qua Strategy Pattern. Producer services sử dụng shared library `notification-client` (Port/Adapter) để enqueue notification request trong cùng business transaction.

| Metric | Giá trị |
|--------|---------|
| Số FR | 24 (URD: 19, Enriched: 5) |
| Issues | 1 (🔴: 0, 🟡: 1) |
| Open Questions | 0 (all resolved) |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Producer Service** (auth-service, account-service, system-admin-service): Tạo notification request qua NotificationPort (client SDK)
- **Notification Service** (scheduler/dispatcher): Poll queue, render template, route tới channel worker, gửi notification
- **Channel Worker** (SmtpEmailSender, SmsSender, PushSender, OttSender): Gửi notification qua channel-specific provider
- **External Provider** (SMTP, Twilio, FCM, Zalo API): Nhận và deliver notification tới end user
- **Webhook Caller**: Provider gửi callback (delivery/read status) về notification-service
- **User / Admin**: Revoke/unsend notification, xem report, quản lý preferences
- **Monitoring System**: Scrape metrics, hiển thị dashboard

## 2. Functional Requirements

### FR-001: Enqueue notification trong business transaction [URD]
- **Actor**: Producer Service
- **Action**: Hệ thống phải cho phép producer service enqueue notification request vào `notification_queue` table trong cùng `@Transactional` của business operation (Transactional Outbox pattern)
- **Validation**: Insert PHẢI atomic với business transaction — rollback nếu DB error

### FR-002: Ngăn chặn duplicate notification [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải ngăn chặn duplicate notification bằng `correlation_id` (unique constraint)
- **Validation**: INSERT với correlation_id trùng → reject (constraint violation)

### FR-003: Poll và process notification queue [URD]
- **Actor**: Notification Service (Scheduler)
- **Action**: Hệ thống phải poll `notification_queue` theo interval cấu hình (mặc định 5 giây) sử dụng `SELECT ... FOR UPDATE SKIP LOCKED` để đảm bảo multi-instance safe
- **Validation**: Nhiều instance chạy song song không xử lý cùng một record

### FR-004: Render template trước khi gửi [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải resolve template_code → render nội dung (subject + body) bằng placeholder substitution `{{key}}`
- **Validation**: Template không tồn tại → status = FAILED, error_message = "Template not found"

### FR-005: Route notification tới channel worker [URD]
- **Actor**: Notification Dispatcher
- **Action**: Hệ thống phải route notification tới channel-specific sender dựa trên `channel` field (EMAIL, SMS, PUSH, OTT) sử dụng Strategy Pattern
- **Validation**: Channel không có sender → status = FAILED, error_message = "Channel not supported"

### FR-006: Gửi email qua SMTP [URD]
- **Actor**: SmtpEmailSender
- **Action**: Hệ thống phải gửi email qua JavaMailSender (SMTP protocol) khi channel = EMAIL
- **Validation**: Gửi thành công → status = SENT, sent_at = now()

### FR-007: Retry exponential backoff khi provider lỗi [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải retry khi provider trả lỗi transient: `next_retry_at = now + base_delay × 2^retry_count`
- **Validation**: retry_count tăng sau mỗi lần retry; base_delay configurable (mặc định 30s)

### FR-008: Max retries configurable per channel [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải dừng retry khi `retry_count >= max_retries` (mặc định 3), set status = FAILED
- **Validation**: Record vượt max_retries không bị pick lại bởi scheduler

### FR-009: Failed notification không block queue [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải sử dụng `SKIP LOCKED` để đảm bảo failed/locked records không block các records khác trong queue
- **Validation**: Records FAILED hoặc đang PROCESSING bởi instance khác bị skip

### FR-010: Provider failure không break business flow [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải đảm bảo provider failure (SMTP down, Twilio error) KHÔNG ảnh hưởng business transaction gốc (fail-safe, async delivery)
- **Validation**: Provider error → notification retry, business operation vẫn thành công

### FR-011: Track delivery status via webhook [URD]
- **Actor**: External Provider / Webhook Listener
- **Action**: Hệ thống phải cung cấp webhook endpoint nhận callback từ provider khi notification được delivered
- **Validation**: Callback với valid message_id → update status = DELIVERED, delivered_at = now()

### FR-012: Track read receipt [URD]
- **Actor**: External Provider / Client App
- **Action**: Hệ thống phải track khi user đọc notification → update status = READ, read_at = now()
- **Validation**: Read callback cho message_id không tồn tại → log warning, ignore

### FR-013: Status lifecycle enforcement [URD]
- **Actor**: Notification Service
- **Action**: Hệ thống phải enforce status transitions: PENDING → PROCESSING → SENT → DELIVERED → READ; REVOKED và CANCELLED là terminal states
- **Validation**: REVOKED status không thể chuyển sang status khác; invalid transition → reject

### FR-014: Revoke notification chưa gửi [URD]
- **Actor**: User / Admin
- **Action**: Hệ thống phải cho phép revoke notification có status PENDING/PROCESSING → set status = CANCELLED
- **Validation**: API POST /notifications/{id}/revoke → status thay đổi thành CANCELLED

### FR-015: Mark sent notification as REVOKED [URD]
- **Actor**: User / Admin
- **Action**: Hệ thống phải cho phép mark notification đã SENT/DELIVERED → status = REVOKED (logical revoke)
- **Validation**: Email/SMS revoke là logical only (DB update), không recall from inbox

### FR-016: Push notification collapse on revoke [URD]
- **Actor**: PushSender
- **Action**: Khi revoke push notification, hệ thống phải gửi FCM update với `collapse_key` để replace message trên device
- **Validation**: FCM collapse thành công → original notification bị thay thế

### FR-017: Export metrics qua Micrometer [URD]
- **Actor**: Monitoring System
- **Action**: Hệ thống phải export metrics (sent, failed, retry, delivered count per channel) qua Micrometer counters, scrape-able bởi Prometheus
- **Validation**: Metrics endpoint `/actuator/prometheus` chứa notification metrics

### FR-018: Cleanup old records [URD]
- **Actor**: Cleanup Scheduler
- **Action**: Hệ thống phải tự động cleanup notification records cũ hơn retention period configurable (mặc định 30 ngày)
- **Validation**: Records SENT older than 30 days bị xóa bởi cleanup job

### FR-019: Manual retry failed notification [URD]
- **Actor**: Admin
- **Action**: Hệ thống phải cho phép admin manual retry notification có status FAILED → re-enqueue (reset retry_count, status = PENDING)
- **Validation**: API POST /notifications/{id}/retry → status = PENDING, retry_count = 0

### FR-020: Client SDK shared library [ENRICHED]
- **Actor**: Producer Service
- **Action**: Hệ thống phải cung cấp shared library `notification-client` (JAR) chứa NotificationPort interface + auto-configuration cho Spring Boot
- **Validation**: Producer service chỉ cần add dependency + inject NotificationPort

### FR-021: Idempotent notification processing [ENRICHED]
- **Actor**: Notification Service
- **Action**: Hệ thống phải đảm bảo idempotent processing — cùng một notification không được gửi 2 lần dù scheduler restart hoặc crash recovery
- **Validation**: PROCESSING status + lock đảm bảo chỉ 1 instance xử lý tại 1 thời điểm

### FR-022: Notification request validation [ENRICHED]
- **Actor**: Notification Service / REST API
- **Action**: Hệ thống phải validate notification request (recipient not blank, channel valid, template_code exists) trước khi enqueue
- **Validation**: Invalid request → 400 Bad Request với ProblemDetail response

### FR-023: Channel toggle configuration [ENRICHED]
- **Actor**: Admin / Configuration
- **Action**: Hệ thống phải cho phép enable/disable từng channel (EMAIL, SMS, PUSH, OTT) qua configuration mà không cần code change
- **Validation**: Channel disabled → notification cho channel đó bị skip (status = CANCELLED)

### FR-024: Circuit breaker cho external providers [ENRICHED]
- **Actor**: Notification Service
- **Action**: Hệ thống phải áp dụng circuit breaker (Resilience4j) cho mỗi external provider để tránh cascade failure khi provider down
- **Validation**: Circuit open → notifications chuyển sang retry mode thay vì gọi provider liên tục

## 3. Non-functional Requirements

- **NFR-001**: Response time enqueue API < 50ms (p99)
- **NFR-002**: Notification processing throughput ≥ 1000 msg/s (single instance)
- **NFR-003**: Database riêng (notification-db) — tách khỏi auth-db
- **NFR-004**: Horizontal scaling — multiple instances chạy song song
- **NFR-005**: Zero message loss — Transactional Outbox đảm bảo at-least-once delivery
- **NFR-006**: Spring Boot 3.x + Kotlin, follow base-core conventions

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Các FR đã được normalize từ 14 Business Rules (BR-001→BR-014) thành 24 FRs riêng biệt.

## 5. Enriched Domain Requirements

### Enriched FRs

| FR-ID | Justification |
|-------|---------------|
| FR-020 | Client SDK pattern — cần thiết để producer services integrate mà không couple trực tiếp tới notification-service DB/code |
| FR-021 | Idempotent processing — critical cho distributed system, prevent duplicate sends on crash/restart |
| FR-022 | Input validation — defensive programming, prevent invalid data vào queue |
| FR-023 | Channel toggle — operational flexibility, disable channel khi provider outage |
| FR-024 | Circuit breaker — resilience pattern, prevent cascade failure |

Enriched count: 5 ≤ min(5, ceil(24 × 0.20)) = min(5, 5) = 5 ✅

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| SMTP Provider (JavaMailSender) | Email delivery | Đã có trong auth-service |
| Twilio SMS Gateway | SMS delivery | Future — REST API |
| Firebase Cloud Messaging (FCM) | Push notification | Future — Firebase Admin SDK |
| Zalo OA API | OTT notification | Future — REST API |
| Telegram Bot API | OTT notification | Future — REST API |
| Kafka | Inter-service messaging (enqueue via topic) | Đã có infra |
| PostgreSQL | Notification-specific database | DB riêng |
| Redis | Deduplication cache, rate limiting | Đã có infra |

## 6. Assumptions

- A-001: notification-service chạy trên cùng Kubernetes cluster với auth-service
- A-002: PostgreSQL cho notification-db được provision riêng (không share auth-db)
- A-003: Kafka cluster hiện có đủ capacity cho notification events
- A-004: Build system (Gradle + base-core conventions) tương thích cho project mới
- A-005: SMS/Push/OTT channels sẽ được implement ở Phase 2 (Phase 1 chỉ EMAIL)

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-016: Push collapse mechanism cần chi tiết hơn (FCM API specifics) |
| Đầy đủ (Completeness) | 20/25 | FR-012: Read receipt mechanism cho Email chưa specify (pixel tracking vs link tracking); FR-011: Webhook format chưa define |
| Nhất quán (Consistency) | 22/25 | FR-015 + FR-016: Revoke behavior khác nhau per channel nhưng cùng API endpoint — cần document rõ |
| Kiểm thử được (Testability) | 17/25 | FR-010: "Provider failure KHÔNG break business flow" khó test tự động; FR-024: Circuit breaker thresholds chưa specify |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -2 | FR-016 | "FCM collapse_key replace message" — chưa specify API call details | Define FCM API payload template |
| 2 | Completeness | -3 | FR-012 | "track khi user đọc notification" — Email read tracking mechanism không rõ | Specify: pixel tracking cho email, client API cho push |
| 3 | Completeness | -2 | FR-011 | "webhook endpoint nhận callback" — webhook format/auth chưa define | Define webhook payload schema + HMAC verification |
| 4 | Consistency | -3 | FR-015, FR-016 | Revoke cho email = logical, push = collapse — behavior khác nhưng cùng API | Document per-channel revoke behavior trong API spec |
| 5 | Testability | -5 | FR-010 | "fail-safe" — khó viết automated test cho provider cascade failure | Add specific test scenario: mock SMTP down → verify business TX ok |
| 6 | Testability | -3 | FR-024 | Circuit breaker "tránh cascade failure" — thresholds chưa define | Specify: failureRateThreshold, waitDurationInOpenState, slidingWindowSize |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Missing | 🟡 | Webhook authentication chưa define — provider callbacks cần HMAC verification | FR-011 | Thêm HMAC signature verification cho webhook endpoint |
| 2 | Incomplete | 🟡 | Read receipt cho Email channel chưa specify mechanism (pixel tracking vs click tracking) | FR-012 | Clarify: dùng pixel tracking (1x1 image) hay link redirect tracking |
| 3 | Risk | 🟡 | Data migration strategy từ auth-db (mail_queue) → notification-db chưa define | FR-001 | Plan: parallel run period → dual write → cutover |

## 9. Open Questions

- **OQ-001**: ~~Email read tracking~~ → ✅ **RESOLVED**: Cho phép sử dụng **cả 2** (pixel tracking + link click tracking), configurable per template via `tracking_mode` field.
- **OQ-002**: ~~Data migration~~ → ✅ **RESOLVED**: Không cần migrate dữ liệu cũ. Xóa table cũ khỏi auth-db, tạo notification-db mới clean với Flyway migration scripts chuẩn.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Notification Infrastructure — multi-channel message delivery platform

### 10.2 Flow Type
Command (enqueue → process → deliver)

### 10.3 Candidate Services
- **auth-service**: Source code notification hiện tại (MailQueueEntity, MailJobScheduler, MailQueueService, OutboxPoller) — cần scan để extract patterns + migration reference
- **notification-service**: Target service (NEWBUILD — chưa tồn tại)
- **account-service**: Reference service — cùng base-core conventions, settings.gradle.kts pattern

### Detection Evidence
- Keyword: `MailQueue`, `MailJobScheduler`, `notification`, `outbox` → Module: `auth-service/application/`, `auth-service/adapter/out/persistence/entity/` → File: `MailQueueEntity.kt`, `MailJobScheduler.kt`, `MailQueueService.kt`
- Keyword: `base-core conventions` → Module: `account-service` → File: `build.gradle.kts`, `settings.gradle.kts`

### 10.4 External Integrations
- SMTP (JavaMailSender) — existing trong auth-service
- Kafka (spring-kafka) — existing infra
- Redis (spring-boot-starter-data-redis) — existing infra
- Twilio REST API — future SMS
- Firebase Admin SDK — future Push
- Zalo OA API / Telegram Bot API — future OTT

### 10.5 Required Modules
- `notification-service` (Spring Boot application — NEWBUILD)
- `notification-client` (shared library JAR — NEWBUILD)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Producer Service | Business event trigger → call NotificationPort.enqueue() | auth-service / any service |
| 2 | NotificationPort | INSERT notification_queue (status=PENDING) within same TX | notification-db |
| 3 | NotificationJobScheduler | Poll queue (SELECT FOR UPDATE SKIP LOCKED) | notification-service |
| 4 | NotificationDispatcher | Resolve channel → route to sender | notification-service |
| 5 | ChannelSender (Email/SMS/Push) | Call external provider | SMTP / Twilio / FCM |
| 6 | ChannelSender | Update status = SENT | notification-db |
| 7 | External Provider | Deliver to end user | External |
| 8 | Webhook Listener | Receive delivery/read callback | notification-service |
| 9 | Webhook Listener | Update status = DELIVERED/READ | notification-db |


## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 BR-001 | Enqueue service | NotificationEnqueueService (NEW) | Pending |
| FR-002 | UC-001 BR-002 | Enqueue service | NotificationQueueEntity (NEW) | Pending |
| FR-003 | UC-002 Basic Flow #1 | Scheduler | NotificationJobScheduler (MIGRATE) | Pending |
| FR-004 | UC-002 Basic Flow #2 | Template service | TemplateRenderService (MIGRATE) | Pending |
| FR-005 | UC-002 Basic Flow #3 | Dispatcher | NotificationDispatcher (NEW) | Pending |
| FR-006 | UC-002 Basic Flow #4 | Email sender | SmtpEmailSender (MIGRATE) | Pending |
| FR-007 | UC-002 BR-003 | Retry logic | NotificationJobScheduler (MIGRATE) | Pending |
| FR-008 | UC-002 BR-004 | Config | NotificationProperties (NEW) | Pending |
| FR-009 | UC-002 BR-005 | Scheduler query | NotificationQueueRepository (MIGRATE) | Pending |
| FR-010 | UC-002 BR-006 | Architecture | Transactional Outbox pattern | Pending |
| FR-011 | UC-003 Basic Flow | Webhook | WebhookController (NEW) | Pending |
| FR-012 | UC-003 Basic Flow #4 | Webhook | WebhookController (NEW) | Pending |
| FR-013 | UC-003 BR-007 | Entity | NotificationStatus enum (NEW) | Pending |
| FR-014 | UC-004 Basic Flow | Revoke API | NotificationRevokeService (NEW) | Pending |
| FR-015 | UC-004 BR-010 | Revoke API | NotificationRevokeService (NEW) | Pending |
| FR-016 | UC-004 BR-011 | Push sender | FcmPushSender (NEW) | Pending |
| FR-017 | UC-005 BR-013 | Metrics | Micrometer counters (NEW) | Pending |
| FR-018 | UC-005 BR-014 | Cleanup | NotificationCleanupScheduler (NEW) | Pending |
| FR-019 | UC-004 BR-009 | Retry API | NotificationController (NEW) | Pending |
| FR-020 | [ENRICHED] | Client SDK | notification-client module (NEW) | Pending |
| FR-021 | [ENRICHED] | Scheduler | NotificationJobScheduler (MIGRATE) | Pending |
| FR-022 | [ENRICHED] | Validation | NotificationController (NEW) | Pending |
| FR-023 | [ENRICHED] | Config | NotificationProperties (NEW) | Pending |
| FR-024 | [ENRICHED] | Resilience | Channel sender wrapper (NEW) | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature này có complexity MEDIUM-HIGH: code migration (auth→notify) + new service setup + multi-channel extension
- 90% core patterns đã tồn tại trong auth-service (MailJobScheduler, MailQueueService, OutboxPoller)
- Rủi ro thấp vì dựa trên proven patterns đã production-ready
- Phase 1 (Email only) đủ để validate architecture trước khi mở rộng channels

### Related Features / Precedents
- `MailJobScheduler.kt` trong auth-service — foundation cho NotificationJobScheduler
- `OutboxPoller.kt` trong auth-service — reference cho polling pattern
- `MailQueueService.kt` — reference cho enqueue logic
- `EventEnvelope.kt` — CloudEvents-inspired envelope pattern có thể reuse

### Integration Notes
- **Kafka**: Đã có infra, topic naming convention follow existing patterns (`notification.enqueue`, `notification.status`)
- **PostgreSQL**: notification-db cần separate Flyway migration baseline
- **Redis**: Dùng cho deduplication cache (correlation_id lookup) + rate limiting
- **base-core**: Tất cả conventions (Snowflake ID, build-logic, spring-app-conventions) apply cho service mới

### Suggested Approach
1. **Phase 1**: Tạo project → migrate MailJobScheduler + MailQueueService → create notification-client SDK → verify end-to-end email flow
2. **Phase 2**: Add Strategy pattern dispatcher → implement SMS/Push senders → channel config
3. **Phase 3**: Webhook endpoints → read receipt → revoke API → dashboard

Ưu tiên reuse `MailJobScheduler` logic (polling, retry, metrics) → refactor thành NotificationJobScheduler. Không viết lại từ đầu.

### Context from Confluence Images
N/A
