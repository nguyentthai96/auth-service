# Business Analysis: Notification Service

## 1. Feature Overview

### 1.1 Mục tiêu

Tách notification infrastructure (outbox + mail sender) từ auth-service thành microservice độc lập, hỗ trợ multi-channel delivery, cho phép tất cả services trong hệ thống insert notification request qua port adapter (client SDK).

### 1.2 Business Value

| Value | Description |
|-------|-------------|
| **Performance Isolation** | Notification processing chạy trên PID riêng, không ảnh hưởng auth response time |
| **Independent Scaling** | Scale notification workers theo load (spike email, campaign SMS) |
| **Multi-channel Ready** | Mở rộng từ Email → SMS, Push, OTT mà không thay đổi producer services |
| **Centralized Management** | Một nơi quản lý templates, retry, tracking, reporting |
| **Reusability** | Mọi service đều gửi notification qua cùng interface (DRY) |

## 2. Use Cases

### UC-001: Enqueue Notification (Producer Side)

**Mô tả:** Bất kỳ microservice nào cần gửi notification cho user sẽ tạo notification request vào queue.

**Actor:** Producer Service (auth-service, account-service, ...)

**Precondition:** Service có notification-client dependency (shared library)

**Basic Flow:**
1. Business event xảy ra (e.g., new device login, order placed)
2. Handler gọi `NotificationPort.enqueue(request)` trong cùng `@Transactional`
3. Request được insert vào `notification_queue` table
4. Transaction commit → request visible cho Notification Service

**Exception Flows:**
- **E1:** Template code không tồn tại → log warning, enqueue với raw data
- **E2:** Database error → rollback business transaction (same TX)

**Business Rules:**
- BR-001: Notification request PHẢI được insert trong cùng business transaction (Transactional Outbox)
- BR-002: Duplicate notification prevention qua correlation_id

---

### UC-002: Process & Deliver Notification (Service Side)

**Mô tả:** Notification Service poll queue, render template, chọn channel, gửi đi.

**Actor:** Notification Service (MailJobScheduler / NotificationDispatcher)

**Precondition:** notification_queue có records với status = PENDING

**Basic Flow:**
1. Scheduler poll `notification_queue` (SELECT FOR UPDATE SKIP LOCKED)
2. Cho mỗi record: resolve template → render content
3. Xác định channel (email/SMS/push) dựa trên request type + user preferences
4. Gọi channel-specific sender (SmtpEmailSender, TwilioSmsSender, ...)
5. Cập nhật status = SENT, set sent_at timestamp
6. Emit delivery metrics

**Exception Flows:**
- **E1:** Template not found → status = FAILED, error_message = "Template not found"
- **E2:** Provider timeout → keep PENDING, retry next cycle (FR-021 pattern)
- **E3:** Provider error (confirmed) → retry_count++, exponential backoff
- **E4:** Max retries exceeded → status = FAILED, move to dead_letter (optional)

**Business Rules:**
- BR-003: Retry strategy: base_delay × 2^retry_count (exponential backoff)
- BR-004: Max retries configurable per channel (default: 3)
- BR-005: Failed notifications KHÔNG block queue (SKIP LOCKED)
- BR-006: Provider failure KHÔNG break business flow (fail-safe)

---

### UC-003: Track Delivery Status

**Mô tả:** Hệ thống track toàn bộ lifecycle của notification từ PENDING → DELIVERED → READ.

**Actor:** Notification Service + External Provider Webhooks

**Basic Flow:**
1. Provider gửi callback/webhook khi message được delivered
2. Webhook endpoint nhận callback, extract message_id
3. Update notification status = DELIVERED, set delivered_at
4. (Optional) Provider gửi read callback → status = READ, set read_at

**Exception Flows:**
- **E1:** Webhook callback cho message_id không tồn tại → log warning, ignore
- **E2:** Callback đến sau khi record đã REVOKED → ignore update

**Business Rules:**
- BR-007: Status transitions: PENDING → PROCESSING → SENT → DELIVERED → READ
- BR-008: REVOKED status không thể chuyển sang status khác (terminal)
- BR-009: FAILED status có thể manual retry (re-enqueue)

---

### UC-004: Revoke / Unsend Notification

**Mô tả:** User hoặc system có thể revoke notification chưa gửi hoặc đánh dấu đã gửi là revoked.

**Actor:** User / Admin / System

**Basic Flow:**
1. API nhận revoke request (notification_id + reason)
2. Check current status:
   - PENDING/PROCESSING → cancel, set status = CANCELLED
   - SENT/DELIVERED → set status = REVOKED
   - FAILED → no action needed
3. Cho push notifications: gửi update/collapse message
4. Log revoke action for audit

**Exception Flows:**
- **E1:** Notification đã READ → vẫn mark REVOKED nhưng user đã đọc (best-effort)
- **E2:** Email/SMS đã gửi → mark REVOKED nhưng không thể recall content

**Business Rules:**
- BR-010: Email/SMS revoke là logical only (mark in DB, không recall from inbox)
- BR-011: Push notification revoke → FCM collapse_key replace message
- BR-012: In-App revoke → WebSocket broadcast to hide message

---

### UC-005: Report & Metrics

**Mô tả:** Quản lý report số lượng notification gửi thành công/thất bại per channel.

**Actor:** Admin / Monitoring System

**Basic Flow:**
1. Micrometer counters track: sent, failed, retry, delivered per channel
2. Prometheus scrape metrics endpoint
3. Grafana dashboard hiển thị:
   - Delivery success rate per channel
   - Retry rate
   - Average delivery time
   - Failed notifications queue depth

**Business Rules:**
- BR-013: Metrics phải export qua Micrometer (consistent với ecosystem)
- BR-014: Daily summary cleanup job cho old SENT records (configurable retention)

## 3. Traceability Matrix

| Use Case | Business Rules | Priority |
|----------|---------------|----------|
| UC-001: Enqueue | BR-001, BR-002 | P0 (Critical) |
| UC-002: Process & Deliver | BR-003, BR-004, BR-005, BR-006 | P0 (Critical) |
| UC-003: Track Delivery | BR-007, BR-008, BR-009 | P1 (Important) |
| UC-004: Revoke/Unsend | BR-010, BR-011, BR-012 | P2 (Nice-to-have) |
| UC-005: Report | BR-013, BR-014 | P1 (Important) |

## 4. Migration Strategy from auth-service

### Phase 1: Extract Notification Module (Current Request)
1. Tạo `notification-service` project mới (cùng settings.gradle.kts pattern)
2. Migrate: MailQueueEntity, MailTemplateEntity, MailJobScheduler, MailQueueService
3. Tạo notification_queue table (unified, thay thế mail_queue)
4. Tạo notification-client shared library (Port interface)
5. Auth-service → dùng notification-client để enqueue (thay vì direct MailQueueService)

### Phase 2: Multi-Channel Extension
1. Abstract NotificationSender interface (Strategy pattern)
2. Implement: SmtpEmailSender (existing), SmsSender, PushSender
3. NotificationDispatcher router
4. Channel-specific configuration

### Phase 3: Tracking & Advanced Features
1. Delivery webhook endpoint
2. Read receipt tracking
3. Revoke/unsend API
4. User notification preferences
5. Grafana dashboard
