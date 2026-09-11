# Handoff Summary: Notification Service

## 1. Feature Quick Reference

| Field | Value |
|-------|-------|
| **Feature** | Notification Service (tách từ auth-service) |
| **Approach** | Custom Build (dựa trên code hiện có) |
| **Priority** | P0 — Core infrastructure service |
| **Effort Estimate** | Phase 1: 2-3 days, Phase 2: 3-5 days, Phase 3: 3-5 days |

## 2. Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Build vs Buy | **Build** | Tech stack alignment (Kotlin/Spring Boot), 90% code đã có |
| Database | **Separate PostgreSQL** | Performance isolation, independent scaling |
| Client SDK | **Shared library (notification-client)** | Port/Adapter pattern, auto-configuration |
| Channel Extension | **Strategy pattern** | Open/Closed principle, zero dispatcher changes |
| Status Lifecycle | **8 states** | PENDING→PROCESSING→SENT→DELIVERED→READ + FAILED, REVOKED, CANCELLED |
| Retry | **Exponential backoff + DLQ** | Existing pattern in MailJobScheduler |
| ID Strategy | **Snowflake** | Consistent with base-core |

## 3. Implementation Phases

### Phase 1: Extract & Foundation (P0 — Current Request)
- [x] Create notification-service project
- [ ] Create notification-client shared library (NotificationPort interface)
- [ ] Migrate MailQueueEntity → NotificationQueueEntity (extended)
- [ ] Migrate MailTemplateEntity → NotificationTemplateEntity
- [ ] Migrate MailJobScheduler → NotificationJobScheduler
- [ ] Migrate MailQueueService → NotificationEnqueueService
- [ ] Create SmtpEmailSender (extract from MailJobScheduler)
- [ ] Create NotificationDispatcher (Strategy pattern router)
- [ ] Update auth-service: replace MailQueueService → NotificationPort
- [ ] Database migration V1 (notification_queue, notification_template)
- [ ] Configuration (NotificationProperties)

### Phase 2: Multi-Channel (P1)
- [ ] SmsSender implementation (Twilio)
- [ ] FcmPushSender implementation (Firebase Admin SDK)
- [ ] Channel configuration toggle (enabled/disabled per channel)
- [ ] Kafka consumer for enqueue events
- [ ] REST API for enqueue + status query

### Phase 3: Tracking & Advanced (P2)
- [ ] Webhook endpoint for delivery callbacks
- [ ] Read receipt tracking
- [ ] Revoke/unsend API
- [ ] Dead letter queue table + migration
- [ ] User notification preferences
- [ ] Metrics dashboard (Grafana)
- [ ] Cleanup scheduler

## 4. Files Reference

| Document | Purpose |
|----------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| [opensource_findings.md](./opensource_findings.md) | OSS evaluation (Novu, Apprise, NotifMe) |
| [web_research.md](./web_research.md) | Architecture patterns, retry strategies |
| [comparison_analysis.md](./comparison_analysis.md) | Approach comparison + recommendation |
| [business_analysis.md](./business_analysis.md) | Use cases, business rules, traceability |
| [technical_spec.md](./technical_spec.md) | Architecture, ERD, API, sequence diagrams |
| [validation_report.md](./validation_report.md) | Research quality verification |

## 5. Current Code to Migrate (From auth-service)

| File | Lines | Complexity |
|------|-------|------------|
| `MailQueueEntity.kt` | 55 | Low |
| `MailTemplateEntity.kt` | 31 | Low |
| `MailStatus.kt` | 17 | Low |
| `MailQueueService.kt` | 75 | Medium |
| `MailJobScheduler.kt` | 124 | Medium-High |
| `MailQueueRepository.kt` | 42 | Low |
| `MailTemplateRepository.kt` | 13 | Low |
| `V16__create_mail_queue.sql` | 24 | Low |
| **Total** | **~381** | |

## 6. Dependencies on auth-service After Migration

| Dependency | Type | Details |
|------------|------|---------|
| notification-client | Library | Shared JAR, NotificationPort interface |
| NewDeviceMailHandler | Adapter | Uses NotificationPort.enqueue() |
| KafkaNotificationGateway | Replace | Replaced by NotificationPort |
| LoggingNotificationGateway | Replace | Replaced by NotificationPort |
| NotificationGateway | Remove | Replaced by NotificationPort |

## 7. Next Steps

Recommended downstream workflow:

```
→ /wf_pre_openspec notification-service
  (Uses this business analysis as URD source)
→ /wf_openspec notification-service  
  (Generate implementation artifacts)
→ /wf_openspec_apply notification-service
  (Execute implementation)
```

Hoặc trực tiếp bắt tay implement Phase 1 nếu user muốn nhanh.
