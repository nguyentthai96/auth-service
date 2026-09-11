# Proposal: notification-service

## 1. Summary

Tách notification infrastructure (outbox + mail sender) từ auth-service thành microservice độc lập — **notification-service**. Service chạy trên PID/process Spring Boot riêng, database PostgreSQL riêng (`notification-db`), hỗ trợ multi-channel delivery (Email, SMS, Push, OTT) qua Strategy Pattern.

## 2. Problem Statement

Hiện tại, notification logic (MailJobScheduler, MailQueueService, mail_queue table) nằm trong auth-service:
- **Performance coupling**: Mail job scheduler chạy cùng PID với auth logic → SMTP timeout ảnh hưởng auth response
- **Scaling limitation**: Không thể scale notification workers độc lập
- **Single channel**: Chỉ hỗ trợ Email, mở rộng SMS/Push yêu cầu sửa auth-service
- **Code duplication risk**: Nếu account-service cũng cần send notification → phải duplicate logic

## 3. Proposed Solution

**Approach**: Extract + Rewrite Lean (Selected from brainstorm — DD-001 through DD-009)

Tạo project notification-service mới, viết lại clean architecture từ đầu dựa trên proven patterns từ auth-service code cũ (~200 LOC). Giữ Transactional Outbox pattern đảm bảo at-least-once delivery.

### Key Architecture Decisions

| Decision | Choice |
|----------|--------|
| Enqueue Strategy | Shared Database (Transactional Outbox) — at-least-once guarantee |
| Channel Extension | Strategy Pattern (`NotificationSender` interface) |
| Client SDK | `notification-client` JAR — publish mavenLocal |
| Multi-datasource | Producer services cấu hình secondary datasource tới notification-db |
| Status Lifecycle | 7 states: PENDING→PROCESSING→SENT→DELIVERED→READ + FAILED/REVOKED/CANCELLED |
| Read Tracking | Pixel + Link click tracking (configurable per template) |
| Resilience | Resilience4j circuit breaker per channel |
| Migration | Clean break — fresh notification-db, drop old mail_queue from auth-db |

## 4. Scope

### In Scope (Phase 1 — Current Request)
- Tạo notification-service project (Spring Boot, Kotlin, Clean Architecture)
- Tạo notification-client shared library (NotificationPort interface)
- Implement NotificationQueueEntity (extended schema)
- Implement NotificationTemplateEntity + seed data
- Implement NotificationJobScheduler (poll + SKIP LOCKED)
- Implement NotificationDispatcher (Strategy Pattern router)
- Implement SmtpEmailSender (Email channel)
- Implement NotificationEnqueueService
- REST API: enqueue, status query, retry, revoke
- Flyway migrations (V1→V3)
- NotificationProperties (@ConfigurationProperties)
- NotificationException + NotificationErrorCode
- Micrometer metrics (sent/failed/retry counters)
- Cleanup scheduler (daily, configurable retention)
- Auth-service: drop mail_queue/mail_templates, remove Mail* code, add notification-client dependency

### Out of Scope (Phase 2+)
- SMS sender (Twilio integration)
- Push sender (Firebase FCM)
- OTT sender (Zalo, Telegram)
- Kafka consumer endpoint
- gRPC endpoint
- Webhook delivery callbacks
- Read receipt tracking (pixel/link)
- Grafana dashboard
- User notification preferences
- Dead letter queue

## 5. Success Criteria

| Criteria | Metric |
|----------|--------|
| Notification enqueue latency | < 50ms p99 |
| Processing throughput | ≥ 100 msg/s (single instance, Phase 1) |
| Zero message loss | Transactional Outbox guarantee |
| Multi-instance safe | SKIP LOCKED concurrency |
| Auth-service clean | Zero notification code remaining |
| Build success | `./gradlew build` passes |
| Template rendering | {{key}} substitution functional |

## 6. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Multi-datasource TX issues | Medium | High | Test with @Transactional boundary verification |
| SMTP timeout blocking scheduler | Low | Medium | Async sending + circuit breaker per channel |
| Template rendering edge cases | Low | Low | Unit tests for placeholder substitution |
| notification-client version conflict | Low | Medium | Publish via mavenLocal, version lock |

## 7. Implementation Phases

| Phase | Scope | Effort | Priority |
|-------|-------|--------|----------|
| Phase 1 | Extract + Foundation (Email only) | 2-3 days | P0 |
| Phase 2 | Multi-Channel (SMS, Push) | 3-5 days | P1 |
| Phase 3 | Tracking + Advanced (Webhook, Revoke, Dashboard) | 3-5 days | P2 |

## 8. FR Coverage

All 24 FRs from pre_openspec.md covered:
- **Phase 1**: FR-001 through FR-010, FR-017, FR-018, FR-020 through FR-024
- **Phase 2**: FR-005 (SMS/Push channels), FR-016 (FCM collapse)
- **Phase 3**: FR-011, FR-012, FR-013, FR-014, FR-015, FR-019
