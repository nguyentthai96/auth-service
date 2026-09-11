---
type: brainstorm_notes
change: notification-service
date: 2026-09-11
selected_direction: "Approach 1: Extract + Rewrite Lean"
pre_flow: "Command"
pre_feature_type: "NEWBUILD"
status: complete
---

# Brainstorm Notes: notification-service

## Date
2026-09-11

## Context

Tách notification infrastructure (outbox mail + send) từ auth-service thành microservice độc lập. User muốn:
- Standalone Spring Boot PID process + DB riêng
- Multi-channel (Email, SMS, Push, OTT)
- Client SDK cho producer services
- Retry, tracking, revoke, report

Source context: `pre_openspec.md` (24 FRs, 82/100 quality), research phase (8 docs), codebase scan (5 context files).

## Questions Asked & Answers

- **Q1 (OQ-001)**: Email read tracking — pixel tracking hay link click tracking?
  → **A**: Cho phép sử dụng **cả 2** (optional, configurable per template). Pixel tracking mặc định, link tracking bổ sung khi cần click analytics.

- **Q2 (OQ-002)**: Data migration — migrate existing mail_queue records từ auth-db?
  → **A**: **Không cần migrate**. Dev environment — xóa table cũ, tạo database mới fresh, quản lý clean Flyway migration scripts.

## Approaches Considered

### Approach 1: Extract + Rewrite Lean ⭐ (SELECTED)

**Mô tả**: Tạo project notification-service mới từ đầu, lấy pattern logic từ auth-service code cũ (MailJobScheduler, MailQueueService) nhưng **viết lại clean** cho multi-channel từ đầu thay vì copy-paste rồi refactor.

```
    ┌─────────────────┐         ┌─────────────────────────┐
    │  auth-service    │         │  notification-service    │
    │                  │         │                          │
    │  ┌────────────┐  │ Kafka   │  ┌──────────────────┐   │
    │  │Notification│──┼────────►│  │ KafkaListener    │   │
    │  │Gateway     │  │  or     │  └────────┬─────────┘   │
    │  │(Kafka)     │  │ Direct  │           │              │
    │  └────────────┘  │  DB     │  ┌────────▼─────────┐   │
    │                  │         │  │ NotificationJob   │   │
    │  ┌────────────┐  │         │  │ Scheduler        │   │
    │  │Notification│──┼──┐      │  │ (SKIP LOCKED)    │   │
    │  │Port (SDK)  │  │  │      │  └────────┬─────────┘   │
    │  └────────────┘  │  │      │           │              │
    └──────────────────┘  │      │  ┌────────▼─────────┐   │
                          │      │  │ Dispatcher        │   │
    ┌─────────────────┐   │      │  │ (Strategy)       │   │
    │  account-service │   │      │  └──┬──┬──┬──┬─────┘   │
    │                  │   │      │     │  │  │  │          │
    │  ┌────────────┐  │   │      │  ┌──▼──▼──▼──▼─────┐   │
    │  │Notification│──┼───┘      │  │ Email│SMS│Push│OTT│  │
    │  │Port (SDK)  │  │   shared │  └─────────────────┘   │
    │  └────────────┘  │    DB    │                          │
    └──────────────────┘          └─────────────────────────┘
                                           │
                                   ┌───────▼───────┐
                                   │notification-db│
                                   │ (PostgreSQL)  │
                                   └───────────────┘
```

- **Pros**:
  - Clean architecture từ đầu — no legacy baggage
  - Multi-channel ready ngay (NotificationStatus thay vì MailStatus)
  - Pattern chuẩn hơn: `channel` field, correlation_id, unified queue
  - Dễ test vì design cho testability
- **Cons**:
  - Effort hơn copy-paste (~2 ngày thay vì 1 ngày)
  - Phải re-implement template rendering (nhưng logic đơn giản)

### Approach 2: Direct Copy + Refactor

**Mô tả**: Copy nguyên MailJobScheduler, MailQueueService, MailQueueEntity sang project mới, rename Mail→Notification, thêm channel field.

- **Pros**: Nhanh, chắc chắn hoạt động (proven code)
- **Cons**: 
  - Mang theo coupling cũ (SecurityProperties reference)
  - Entity design không optimal (thiếu correlation_id, channel, delivered_at, read_at)
  - Phải refactor lớn ở Phase 2 khi thêm multi-channel

### Approach 3: Event-Driven Only (Kafka → Worker)

**Mô tả**: Bỏ Transactional Outbox, producer services publish trực tiếp lên Kafka, notification-service chỉ consume.

- **Pros**: Đơn giản hơn, không cần shared DB
- **Cons**:
  - ❌ Mất at-least-once guarantee (Kafka publish có thể fail sau business TX commit)
  - ❌ Vi phạm BR-001 (notification PHẢI trong cùng business TX)
  - ❌ Dual-write problem (DB + Kafka = not atomic)

## Selected Direction

**Approach 1: Extract + Rewrite Lean** — viết lại clean architecture từ đầu nhưng lấy proven patterns từ code cũ. Giữ Transactional Outbox vì đảm bảo at-least-once delivery atomically.

**Lý do**:
1. Code cũ chỉ ~200 LOC (MailJobScheduler 124, MailQueueService 75, Entity 55) — rewrite effort rất thấp
2. Cần thêm nhiều fields mới (channel, correlation_id, delivered_at, read_at, revoked_at, priority) — refactor entity cũ sẽ messy hơn viết mới
3. Clean from day one → dễ maintain long term

---

## Design Decisions

### DD-001: Enqueue Strategy — Shared Database (Transactional Outbox)

```
  Producer Service                notification-service
  ┌──────────────┐               ┌──────────────┐
  │ @Transactional│               │ JobScheduler │
  │   business()  │               │   poll()     │
  │   INSERT INTO │               │   SELECT     │
  │   notif_queue │               │   FOR UPDATE │
  │   COMMIT      │               │   SKIP LOCKED│
  └──────────────┘               └──────────────┘
         │                              │
         └──────────────────────────────┘
                 notification-db
```

**Decision**: Shared Database. notification-client SDK insert trực tiếp vào `notification_queue` table trong cùng `@Transactional` của producer.

### DD-002: Multi-Datasource Strategy cho Producer Services

Producer services cần secondary datasource tới notification-db. notification-client auto-configure via `@ConfigurationProperties` (prefix: `app.notification.datasource`).

### DD-003: Channel Extension — Strategy Pattern

```
  interface NotificationSender {
    fun channel(): NotificationChannel
    fun send(notification: NotificationQueueEntity)
    fun supports(channel: NotificationChannel): Boolean
  }
```

NotificationDispatcher auto-discover tất cả `NotificationSender` beans, route theo `channel` field. Phase 1 chỉ implement `SmtpEmailSender`.

### DD-004: Status Lifecycle — 7 states

```
  PENDING → PROCESSING → SENT → DELIVERED → READ
                │
                └→ FAILED (terminal, but can manual retry → PENDING)
  PENDING → CANCELLED (pre-send cancellation)
  SENT/DELIVERED → REVOKED (logical revoke)
```

### DD-005: Read Tracking (OQ-001 Resolved)

Hỗ trợ **cả 2**: Pixel tracking + Link click tracking, configurable per template via `tracking_mode`: `PIXEL`, `LINK`, `BOTH`, `NONE`. Default: `NONE`.

### DD-006: Migration Strategy (OQ-002 Resolved)

**Clean break** — notification-db fresh Flyway baseline. Auth-service: `V22__drop_mail_tables.sql`.

### DD-007: Project Structure — Clean Architecture

```
notification-service/
├── notification/domain/model/          ← enums, value objects
├── notification/domain/service/        ← template rendering
├── notification/application/port/in/   ← use case interfaces
├── notification/application/port/out/  ← sender + repo interfaces
├── notification/application/           ← services, scheduler, dispatcher
├── notification/adapter/in/web/        ← REST controllers
├── notification/adapter/in/kafka/      ← Kafka listeners
├── notification/adapter/out/persistence/ ← JPA entities + repos
├── notification/adapter/out/sender/    ← channel sender implementations
├── notification/config/                ← properties
├── shared/exception/                   ← error handling
└── notification-client/                ← subproject (shared JAR)
```

### DD-008: notification_queue Extended Schema

Key new fields vs old mail_queue:
- `correlation_id` (VARCHAR 100, UNIQUE) — dedup
- `channel` (VARCHAR 20) — EMAIL/SMS/PUSH/OTT
- `priority` (VARCHAR 10) — LOW/NORMAL/HIGH/URGENT
- `delivered_at`, `read_at`, `revoked_at` — lifecycle timestamps
- `revoke_reason` (VARCHAR 500)
- `source_service` (VARCHAR 100) — producer identification

### DD-009: Resilience — Circuit Breaker per Channel

Resilience4j circuit breaker **per channel**:
- `failureRateThreshold`: 50%
- `slidingWindowSize`: 10 calls
- `waitDurationInOpenState`: 30s
- `permittedNumberOfCallsInHalfOpenState`: 3

---

## Open Questions for Design Phase

- [RESOLVED] OQ-001: Email read tracking → **cả 2** (pixel + link), configurable per template
- [RESOLVED] OQ-002: Data migration → **clean break**, no migration, drop old tables
- [RESOLVED] DD-002: Multi-datasource → **Confirmed**: Producer services sẽ cấu hình multi-datasource (primary = service DB, secondary = notification-db). notification-client auto-config secondary datasource via `@ConfigurationProperties`.
- [RESOLVED] notification-client packaging → **mavenLocal**: Publish notification-client JAR lên `mavenLocal`, producer services thêm dependency như các base-core starters hiện tại.

## Open Questions for URD Analysis

Không còn — URD đã đầy đủ từ pre_openspec.md (82/100 quality).
