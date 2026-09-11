# Web Research: Notification Service Architecture

## 1. Research Iterations

### Iteration 1: Notification Microservice Architecture Pattern
**Queries:** `notification microservice architecture pattern outbox transactional Spring Boot multi-channel`
**Sources found:** 18

**Key Findings:**
- Transactional Outbox Pattern là tiêu chuẩn cho reliable notification delivery
- Fan-out Worker Pattern: tách riêng workers cho mỗi channel (EmailWorker, SmsWorker, PushWorker)
- SELECT ... FOR UPDATE SKIP LOCKED cho multi-instance safe polling
- Circuit Breaker (Resilience4j) cho external provider calls
- Idempotency key (correlation_id) để tránh duplicate notifications

### Iteration 2: Open Source Notification Infrastructure
**Queries:** `Novu Courier notification infrastructure comparison 2025`
**Sources found:** 11

**Key Findings:**
- **Novu**: Leading OSS, TypeScript, self-hostable, workflow engine + digest + delay
- **Courier**: Enterprise SaaS, 50+ integrations, closed source
- **Knock**: SaaS alternative, real-time focus
- **Apprise**: Python wrapper library, 100+ services, stateless
- Trend: Self-hosted OSS notification platforms đang rất phổ biến

### Iteration 3: Retry Strategy & Message Tracking
**Queries:** `notification retry strategy read receipt revoke unsend Spring Boot Kotlin`
**Sources found:** 21

**Key Findings:**
- **Exponential Backoff with Jitter**: base_delay × 2^retry + random jitter để tránh thundering herd
- **Dead Letter Queue (DLQ)**: Sau max retries → move to DLQ cho manual review
- **Delivery Status Tracking**: PENDING → PROCESSING → SENT → DELIVERED → READ → FAILED
- **Read Receipts**: Webhook callbacks từ providers (SendGrid, Twilio) hoặc client-initiated via API
- **Revoke/Unsend**: 
  - Push: `apns-collapse-id` / FCM `collapse_key` để replace
  - In-App: Set status REVOKED + broadcast qua WebSocket
  - Email/SMS: Không thể truly revoke, chỉ mark REVOKED trong system

## 2. Architecture Patterns (Curated)

### 2.1 Event-Driven Notification Architecture

```
Producer Services                 Notification Service
┌─────────────┐                  ┌─────────────────────────────┐
│ auth-service │──┐              │                             │
├─────────────┤  │   Kafka/     │  ┌─────────────┐            │
│ account-svc │──┼──►Direct──►──│  │ Dispatcher   │            │
├─────────────┤  │   Insert     │  │ (Router)     │            │
│ order-svc   │──┘              │  └──┬──┬──┬──┬──┘            │
│ ...         │                 │     │  │  │  │               │
└─────────────┘                 │  ┌──▼──▼──▼──▼──┐           │
                                │  │Channel Workers│           │
                                │  │ Email │ SMS   │           │
                                │  │ Push  │ OTT   │           │
                                │  └───────────────┘           │
                                │                             │
                                │  ┌──────────────┐           │
                                │  │ Webhook       │           │
                                │  │ Listener      │◄─── Provider Callbacks
                                │  └──────────────┘           │
                                └─────────────────────────────┘
```

### 2.2 Strategy Pattern cho Multi-Channel

```kotlin
// Port (domain)
interface NotificationSender {
    fun supports(channel: NotificationChannel): Boolean
    fun send(request: SendRequest): SendResult
}

// Implementations (adapters)
class SmtpEmailSender : NotificationSender
class TwilioSmsSender : NotificationSender
class FcmPushSender : NotificationSender
class ZaloOttSender : NotificationSender

// Router/Dispatcher
class NotificationDispatcher(
    private val senders: List<NotificationSender>
) {
    fun dispatch(request: SendRequest): SendResult {
        val sender = senders.first { it.supports(request.channel) }
        return sender.send(request)
    }
}
```

### 2.3 Notification Status Lifecycle

```
PENDING → PROCESSING → SENT → DELIVERED → READ
              │                    │
              └── FAILED ─────────┘
                    │
              DEAD_LETTERED
              
Special states:
- REVOKED (user requested unsend)
- CANCELLED (before sending)
- EXPIRED (TTL exceeded)
```

### 2.4 Client SDK / Port Adapter Pattern

```kotlin
// Shared library (notification-client)
interface NotificationPort {
    fun enqueue(request: NotificationRequest): NotificationId
}

// Implementation 1: Direct DB insert (same DB)
class DirectNotificationAdapter(
    private val repository: NotificationQueueRepository
) : NotificationPort

// Implementation 2: Kafka publish (separate DB)
class KafkaNotificationAdapter(
    private val kafkaTemplate: KafkaTemplate<String, String>
) : NotificationPort

// Implementation 3: REST API call
class RestNotificationAdapter(
    private val restClient: RestClient
) : NotificationPort
```

## 3. Best Practices Summary

| Category | Best Practice | Source |
|----------|--------------|-------|
| Reliability | Transactional Outbox + SELECT FOR UPDATE SKIP LOCKED | Medium, Spring.io |
| Retry | Exponential backoff with jitter, max 3-5 retries | Dev.to, SuprSend |
| Idempotency | Correlation ID / Message ID deduplication | Substack, Medium |
| Multi-channel | Strategy pattern + Channel-specific workers | SuprSend, Codemia |
| Observability | Micrometer metrics: delivery rate, retry count, latency per channel | DZone |
| Circuit Breaker | Resilience4j per external provider | Spring.io |
| Scaling | Separate DB, horizontal pod scaling | SuprSend |
| Revoke | Status flag + WebSocket broadcast for in-app | StackOverflow |
| Template | Template engine with placeholder substitution | Novu docs |

## 4. Technology Recommendations

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Framework | Spring Boot 3.x + Kotlin | Tương thích base-core |
| Database | PostgreSQL (riêng) | Consistent với ecosystem |
| Messaging | Kafka (existing) | Đã có infra |
| Cache | Redis (existing) | Rate limiting, deduplication |
| Email | JavaMailSender (SMTP) | Đã implement |
| SMS | REST client (Twilio/custom) | Future extensible |
| Push | Firebase Admin SDK | Industry standard |
| OTT | REST client per provider | Zalo, Telegram APIs |
| Monitoring | Micrometer + Prometheus | Đã có stack |
| Circuit Breaker | Resilience4j | Đã có dependency |
