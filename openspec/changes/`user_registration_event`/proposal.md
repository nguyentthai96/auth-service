# Proposal: user-registration-event

> **Change**: user_registration_event | **Type**: EXTEND | **Flow**: Command
> **Direction**: Incremental Event Sourcing — Outbox-First with Deferred Full ES (from brainstorm)
> **_Generated**: 2025-08-21_

## Changes

- **auth.domain.event [NEW]**: `UserRegisteredEvent.kt` — enriched event payload (userId, username, email, fullName, phone, domainCode, domainId, status, registrationSource, ipAddress, userAgent) with eventType `iam.user.registered`, consolidating duplicate definitions from `EventPublisher.kt` and `AuthDomainEvents.kt` (FR-001, FR-002).
- **auth.domain.event [NEW]**: `EventEnvelope.kt` — CloudEvents-inspired generic envelope (`id`, `type`, `source`, `specversion`, `time`, `correlationId`, `schemaVersion`, `data`) wrapping all domain events (FR-005, FR-006, FR-014).
- **auth.application.event [NEW]**: `EventService.kt` — intermediary service encapsulating event creation → event store persistence → outbox insertion in single transaction (DD-003). Reusable across all command handlers.
- **auth.application.port.out [NEW]**: `EventStorePort.kt` — outbound port for event store persistence. `OutboxPort.kt` — outbound port for transactional outbox.
- **auth.application.port.out [MODIFY]**: `EventPublisher.kt` — remove `UserRegisteredEvent` class (moved to domain.event package); keep `DomainEvent` interface, other event types unchanged.
- **auth.application.command [MODIFY]**: `RegisterCommand.kt` — add `ipAddress`, `userAgent`, `correlationId` fields for event enrichment.
- **auth.application.command [MODIFY]**: `RegisterHandler.kt` — replace direct `eventPublisher.publish()` with `eventService.record()` for transactional event sourcing.
- **auth.application.command [DELETE]**: `AuthDomainEvents.kt` — remove duplicate `UserRegisteredEvent`; keep `UserLoggedInEvent`, `SessionRevokedEvent` (used by LoginHandler, RevokeSessionsHandler).
- **auth.adapter.out.persistence [NEW]**: `EventStorePersistenceAdapter.kt` — JPA adapter for event store. `OutboxPersistenceAdapter.kt` — JPA adapter for outbox table.
- **auth.adapter.out.persistence.entity [NEW]**: `EventStoreEntity.kt` — append-only event log entity. `EventOutboxEntity.kt` — transactional outbox entity. `ProcessedEventEntity.kt` — consumer-side idempotency entity.
- **auth.adapter.out.persistence.repository [NEW]**: `EventStoreJpaRepository.kt`, `EventOutboxJpaRepository.kt`, `ProcessedEventJpaRepository.kt`.
- **auth.adapter.out.event [NEW]**: `OutboxPoller.kt` — `@Scheduled` polling for pending outbox entries, publishes to Kafka via `KafkaTemplate`, uses `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety.
- **auth.adapter.out.event [MODIFY]**: `KafkaEventPublisher.kt` — fix topic mismatch bug; use `TOPIC_PREFIX` for proper `iam.` prefixed topics; add partition key support (userId).
- **auth.adapter.in.web [MODIFY]**: `CqrsAuthController.kt` — pass `ipAddress`, `userAgent`, `correlationId` (from `X-Correlation-ID` header) to `RegisterCommand`.
- **shared.config [ADD]**: `OutboxProperties.kt` — configurable outbox poller interval, batch size, max retry count.
- **shared.exception [ADD]**: `AuthErrorCode` — new error codes AUTH_050–AUTH_053 for event store operations.
- **DB migration [NEW]**: `V11__event_sourcing_tables.sql` — `event_store`, `event_outbox`, `processed_events` tables with indexes.

**Total**: ~15 new files, ~5 modified files, ~1 deleted file. 22 FRs in 3 phases. ~5-7 developer-days effort.

---

## 1. Executive Summary

Nâng cấp hệ thống sự kiện đăng ký người dùng (`UserRegisteredEvent`) trong auth-service lên production-grade theo kiến trúc Event Sourcing incremental (Approach B — Outbox-First). Feature này giải quyết 3 vấn đề cốt lõi: (1) payload event quá minimal, (2) fire-and-forget publishing gây mất event khi Kafka down, (3) không có event store cho audit trail. Đồng thời fix critical bug topic mismatch giữa `KafkaEventPublisher` (gửi `"user.registered"`) và `ProfileKafkaListener` (subscribe `"iam.user.registered"`).

### Business Value
- **Event Sourcing nền tảng**: Event store + transactional outbox đảm bảo zero event loss, audit trail đầy đủ cho compliance.
- **Reliability**: Transactional outbox thay thế fire-and-forget publishing — events được persist trong cùng DB transaction, relay bất đồng bộ ra Kafka.
- **Bug fix**: Khắc phục topic mismatch khiến account-service không nhận được registration events (silent failure).
- **Extensibility**: `EventService` pattern reusable cho tất cả command handlers — chuẩn bị cho migration các event types khác (login, session, permission) sang outbox pattern.

### Key Metrics
| Metric | Value |
|--------|-------|
| Functional Requirements | 22 (Idea: 18, Enriched: 4) |
| Phase 1 FRs (in scope) | 15 (FR-001 through FR-010, FR-014, FR-019 through FR-022) |
| Phase 2 FRs (deferred) | 7 (FR-011, FR-012, FR-013, FR-015, FR-016, FR-017, FR-018) |
| New DB tables | 3 (event_store, event_outbox, processed_events) |
| New Kotlin files | ~15 |
| Modified files | ~5 |
| Estimated effort | 5-7 developer-days |

---

## 2. Problem Statement

auth-service hiện tại có infrastructure event publishing cơ bản nhưng thiếu production-grade features:

1. **Minimal event payload**: `UserRegisteredEvent(userId, username, domainCode)` — thiếu email, fullName, phone, roles, ipAddress, userAgent cho downstream consumers.
2. **Duplicate event definitions**: `UserRegisteredEvent` tồn tại ở cả `EventPublisher.kt` (eventType=`"user.registered"`) và `AuthDomainEvents.kt` (eventType=`"USER_REGISTERED"`). RegisterHandler import từ `AuthDomainEvents.kt`.
3. **Fire-and-forget publishing**: `eventPublisher.publish()` được gọi sau persistence, không có transactional guarantee. Events bị mất nếu Kafka down.
4. **No event store**: Events chỉ tồn tại trong Kafka retention window — không có replay capability, không có audit trail.
5. **Topic mismatch bug** 🔴: `KafkaEventPublisher` gửi event lên topic `event.eventType` trực tiếp → `"user.registered"`. Nhưng `ProfileKafkaListener` (account-service) subscribe `"iam.user.registered"`. Kết quả: account-service **không nhận được registration events** — profile auto-creation silent failure.
6. **No schema versioning**: Không có mechanism để evolve event payload mà không break consumers.
7. **No idempotent consumers**: Kafka retry có thể tạo duplicate side effects ở downstream.

---

## 3. Scope Definition

### Phase 1 — Core (This Feature)
| FR | Description | Priority |
|----|-------------|----------|
| FR-001 | Enriched UserRegisteredEvent payload | P0 |
| FR-002 | Hợp nhất duplicate UserRegisteredEvent | P0 |
| FR-003 | Event schema versioning | P1 |
| FR-004 | Event store persistence (PostgreSQL) | P0 |
| FR-005 | Correlation ID cho event tracing | P1 |
| FR-006 | CloudEvents-inspired envelope | P1 |
| FR-007 | Transactional outbox pattern | P0 |
| FR-008 | Idempotent consumer support | P1 |
| FR-009 | Event replay (basic — query from event_store) | P2 |
| FR-010 | Kafka topic standardization | P0 |
| FR-014 | Event metadata enrichment | P1 |
| FR-019 | Registration command idempotency | P1 |
| FR-020 | Full transaction logging | P1 |
| FR-021 | Timeout handling cho Kafka publish | P1 |
| FR-022 | Retry mechanism cho failed delivery | P1 |

### Phase 2 — Deferred
| FR | Description | Reason |
|----|-------------|--------|
| FR-011 | CQRS read-model projection | Complex — needs separate aggregate boundary |
| FR-012 | Downstream consumer contracts | Documentation — low urgency |
| FR-013 | Audit trail từ events | `AuditLogService` already exists; event store provides audit trail inherently |
| FR-015 | Dead letter queue | `KafkaConfig.kt` DLQ already configured |
| FR-016 | Two-tier cache invalidation | Permission cache invalidation already works via `PermissionChangedConsumer` |
| FR-017 | Integration với eventsourcing-utils | RegisterCommand/Handler already extend base types |
| FR-018 | Event snapshots | Not needed until event volume warrants it |

---

## 4. Architecture Approach

**Selected Direction**: Incremental Event Sourcing — Outbox-First with Deferred Full ES (DD from brainstorm).

### Key Design Decisions
| DD-ID | Decision | Rationale |
|-------|----------|-----------|
| DD-001 | Per-event-type Kafka topics (`iam.user.registered`) | Existing `ProfileKafkaListener` subscribes to per-type topics; independent scaling |
| DD-002 | CloudEvents-inspired envelope (no SDK) | Avoids 3 new transitive deps for 6 fields |
| DD-003 | `EventService` intermediary | Keeps handlers focused on domain logic; reusable across all command handlers |
| DD-004 | Snowflake ID for event_store/outbox PKs | Consistent with project convention (all entities use Snowflake IDs from base-core) |
| DD-005 | Single V11 migration for all 3 tables | Atomic feature deployment |
| DD-006 | `SELECT ... FOR UPDATE SKIP LOCKED` outbox poller | Multi-instance safe from day 1 |
| DD-007 | User aggregate = registration events only | Login events 100x volume; start narrow |
| DD-008 | Keep fire-and-forget for non-critical events | AuditEvent, PermissionChangedEvent don't need outbox ater if needed |
| Event store grows unbounded | MED | LOW | Archive >90 days; BRIN index on created_at |
| Dual-write divergence (users + event_store) | LOW | MED | Both in same @Transactional |
| ProfileKafkaListener payload format change | MED | MED | Backward-compatible (Jackson ignores unknown properties); coordinated deployment |
| Outbox duplicate publishing (multi-instance) | LOW | LOW | `SELECT FOR UPDATE SKIP LOCKED` + idempotent consumers |

---

## 6. Dependencies

| Dependency | Type | Status |
|-----------|------|--------|
| PostgreSQL | DB | ✅ Existing |
| Kafka | MQ | ✅ Existing |
| base-core (SnowflakePersistentAuditableEntity) | Library | ✅ Existing |
| eventsourcing-utils (Command/CommandHandler) | Library | ✅ Existing |
| Spring Data JPA | Framework | ✅ Existing |
| Jackson (ObjectMapper) | Serialization | ✅ Existing |
| Flyway | Migration | ✅ Existing (V1-V10) |

---

## 7. Success Criteria

1. `UserRegisteredEvent` has enriched payload (9+ fields) — single definition
2. All registration events persisted in `event_store` table (append-only)
3. Transactional outbox ensures zero event loss (atomic with user persistence)
4. Topic mismatch bug fixed — `ProfileKafkaListener` receives events
5. Outbox poller safely operates across multiple service instances
6. Schema versioning present on all events (v1 baseline)
7. Correlation ID traceable across services
8. No breaking changes for existing consumers (backward-compatible)
