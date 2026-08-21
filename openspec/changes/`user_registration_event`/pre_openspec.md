# Pre-OpenSpec: user-registration-event

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD)
> **Classification Evidence**: keyword `UserRegisteredEvent` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`; keyword `RegisterHandler` → module `auth.application.command` → file `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`; keyword `EventPublisher` → module `auth.application.port.out` → file `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
> **Archive**: N/A
> **Quality Score**: 78/100

## 📋 Feature Summary

Nâng cấp hệ thống sự kiện đăng ký người dùng (`UserRegisteredEvent`) trong auth-service lên mức production-grade theo kiến trúc Event Sourcing. Hiện tại, sự kiện đăng ký người dùng có payload tối giản (userId, username, domainCode) và tồn tại hai định nghĩa trùng lặp (trong `EventPublisher.kt` với eventType `"user.registered"` và `AuthDomainEvents.kt` với eventType `"USER_REGISTERED"`). Tính năng này bao gồm: enrichment payload event, event store persistence (PostgreSQL), transactional outbox, schema versioning, CloudEvents envelope, idempotent consumers, event replay/projection, và Kafka topic standardization. Hệ thống tích hợp với `eventsourcing-utils` library và base-core auto-configuration.

| Metric | Giá trị |
|--------|---------|
| Số FR | 22 (Idea: 18, Enriched: 4) |
| Issues | 4 (🔴: 1, 🟡: 3) |
| Open Questions | 3 |
| **Quality Score** | **78/100** |

---

## 1. Actors

- **Client (End User)**: Gửi request đăng ký tài khoản qua REST API
- **Hệ thống (auth-service)**: Xử lý registration command, persist user, publish domain events, maintain event store
- **Admin**: Quản lý event replay, audit trail, monitoring
- **Downstream Consumers (account-service)**: Nhận UserRegisteredEvent để tạo profile, gán roles mặc định
- **Infrastructure (Kafka, PostgreSQL, Redis)**: Event transport, event store, cache layer

## 2. Functional Requirements

### FR-001: Enriched UserRegisteredEvent payload [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải mở rộng payload của UserRegisteredEvent để bao gồm: userId, username, email, fullName, phone, domainCode, roles (danh sách), ipAddress, userAgent, registeredAt (ISO-8601 timestamp)
- **Validation**: Tất cả required fields có giá trị non-null; email đúng format; registeredAt theo UTC

### FR-002: Hợp nhất duplicate UserRegisteredEvent [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải hợp nhất hai định nghĩa UserRegisteredEvent (trong `EventPublisher.kt` eventType=`"user.registered"` và `AuthDomainEvents.kt` eventType=`"USER_REGISTERED"`) vào một file duy nhất với naming convention dot-notation (`iam.user.registered`)
- **Validation**: Chỉ còn một định nghĩa UserRegisteredEvent; tất cả references updated; compile thành công

### FR-003: Event schema versioning [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải hỗ trợ versioning cho event schema — mỗi event có trường schemaVersion (integer), cho phép backward-compatible evolution. Khi replay events cũ, upcaster mechanism transform old schema → current schema
- **Validation**: SchemaVersion tồn tại trong mọi event; upcaster chain transform v1 → v2 → vN; old consumers đọc được new events (backward-compatible)

### FR-004: Event store persistence [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải persist mọi domain event vào bảng event_store (PostgreSQL) dưới dạng append-only — mỗi event có eventId (UUID), aggregateType, aggregateId, eventType, schemaVersion, payload (JSONB), metadata (JSONB), createdAt
- **Validation**: Mọi event đã publish có record tương ứng trong event_store; eventId unique (UUID); bảng event_store chỉ cho phép INSERT (không UPDATE/DELETE)

### FR-005: Correlation ID cho event tracing [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải gắn correlationId (UUID) vào mỗi event để tracing toàn bộ flow từ command → event → downstream processing
- **Validation**: CorrelationId tồn tại trong mọi domain event; có thể trace cross-service bằng correlationId

### FR-006: Event envelope theo CloudEvents [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải sử dụng CloudEvents specification làm envelope cho domain events, bao gồm id, source, type, time, datacontenttype, và data
- **Validation**: Event payload tuân thủ CloudEvents 1.0 spec; trường `source` = `auth-service`, `type` = event type

### FR-007: Transactional outbox pattern [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải sử dụng transactional outbox pattern để đảm bảo event persistence và publishing là atomic — event được lưu vào outbox table trong cùng transaction với aggregate state change, rồi relay ra Kafka bất đồng bộ
- **Validation**: Event không bao giờ mất dù Kafka down; không có ghost events (events published nhưng không persist)

### FR-008: Idempotent consumer support [IDEA]
- **Actor**: Downstream consumers
- **Action**: Hệ thống phải cung cấp deduplication key (eventId) trong mỗi event để downstream consumers thực hiện idempotent processing
- **Validation**: Consumer có thể skip duplicate events dựa trên eventId; re-delivery không tạo duplicate side effects

### FR-009: Event replay cho aggregate state [IDEA]
- **Actor**: Admin / Hệ thống
- **Action**: Hệ thống phải hỗ trợ replay events từ event store để rebuild aggregate state (User) tại bất kỳ thời điểm nào
- **Validation**: Có thể replay toàn bộ events cho một aggregate; state sau replay == state hiện tại

### FR-010: Kafka topic design cho registration [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải publish UserRegisteredEvent ra Kafka topic `iam.user.registered` với partition key = userId để đảm bảo ordering per user
- **Validation**: Tất cả events của cùng user đi vào cùng partition; topic name theo convention `iam.<aggregate>.<event>`

### FR-011: CQRS read-model projection [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải hỗ trợ read-model projections được build từ events — cho phép query user state từ event log thay vì trực tiếp từ aggregate table
- **Validation**: Projection sync với event store; query từ projection trả kết quả chính xác

### FR-012: Downstream consumer contracts [IDEA]
- **Actor**: Downstream consumers (account-service)
- **Action**: Hệ thống phải định nghĩa rõ ràng consumer contracts cho UserRegisteredEvent, bao gồm required fields và optional fields cho mỗi consumer
- **Validation**: Mỗi consumer có documented contract; contract changes trigger compatibility check

### FR-013: Audit trail từ events [IDEA]
- **Actor**: Admin
- **Action**: Hệ thống phải tạo audit trail tự động từ domain events — mỗi registration event được ghi nhận với timestamp, actor, action, và metadata
- **Validation**: Audit trail query theo userId, timeRange; không thiếu event nào

### FR-014: Event metadata enrichment [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tự động enrich event metadata bao gồm timestamp (ISO-8601), source service name, aggregate type, aggregate id trước khi publish
- **Validation**: Mọi event có đầy đủ metadata; timestamp chính xác theo UTC

### FR-015: Dead letter queue cho failed events [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải routing failed events vào dead letter topic (`iam.user.registered.DLT`) khi consumer xử lý thất bại sau max retries
- **Validation**: Failed events không mất; DLT topic nhận đúng failed events; có monitoring/alerting cho DLT

### FR-016: Two-tier cache invalidation on registration [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải invalidate relevant cache entries (L1 Caffeine + L2 Redis) khi UserRegisteredEvent xảy ra — đảm bảo permission cache và user cache consistent
- **Validation**: Cache invalidated within configured TTL; no stale user data served after registration

### FR-017: Integration với eventsourcing-utils library [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tích hợp event store và CQRS patterns với library `eventsourcing-utils` (com.ntt:eventsourcing-utils:0.0.1-SNAPSHOT) — sử dụng Command/CommandHandler base types
- **Validation**: RegisterCommand extends Command; RegisterHandler extends CommandHandler; events tuân thủ library conventions

### FR-018: Event snapshot cho performance [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải hỗ trợ event snapshots — lưu aggregate state snapshot sau mỗi N events để tăng performance khi replay
- **Validation**: Snapshot interval configurable; replay từ snapshot + remaining events = correct state

### FR-019: Idempotency cho registration command [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải đảm bảo idempotency cho registration command — gọi lại cùng RegisterCommand không tạo duplicate user
- **Validation**: Duplicate request nhận response thành công mà không tạo user mới; idempotency key = username + email

### FR-020: Full transaction logging cho registration [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải log toàn bộ registration transaction bao gồm command received, validation result, user created, event published, Kafka delivery confirmation
- **Validation**: Mỗi step trong transaction flow có structured log entry; correlation tracing end-to-end

### FR-021: Timeout handling cho Kafka publish [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải xử lý timeout khi publish event ra Kafka — nếu Kafka không phản hồi trong 5s, event vẫn persist trong outbox và sẽ được retry
- **Validation**: Kafka timeout không gây mất event; retry mechanism hoạt động đúng

### FR-022: Retry mechanism cho failed event delivery [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải retry gửi event ra Kafka tối đa 3 lần với exponential backoff (1s, 2s, 4s) khi delivery thất bại
- **Validation**: Retry attempts logged; sau 3 lần thất bại event chuyển vào DLT

## 3. Non-functional Requirements

- **Performance**: Event publish latency < 50ms (95th percentile); event replay throughput > 1000 events/s
- **Reliability**: Zero event loss — transactional outbox đảm bảo at-least-once delivery
- **Scalability**: Kafka partitioning cho horizontal scaling; event store hỗ trợ > 10M events
- **Observability**: Structured logging cho mọi event lifecycle stage; metrics cho publish rate, consumer lag
- **Security**: Sensitive fields (email, phone) encrypted at rest trong event store; PII masking in logs
- **Consistency**: Event ordering guaranteed per aggregate (partition key = userId)

---

## 4. Deduplicated & Consolidated

- FR-015 (DLT) và FR-022 (retry) có overlap về failure handling — đã phân tách: FR-022 = retry mechanism trước DLT, FR-015 = DLT routing sau khi max retries exhausted
- FR-005 (correlationId) và FR-014 (metadata enrichment) có overlap — đã phân tách: FR-005 = correlation/tracing, FR-014 = event metadata (timestamp, source, aggregate info)

## 5. Enriched Domain Requirements

Enriched FRs: 4 (FR-019, FR-020, FR-021, FR-022) — trong giới hạn min(5, ceil(22 × 0.20)) = min(5, 5) = 5

### Enriched FRs

- **FR-019**: Idempotency cho registration command — đảm bảo duplicate requests không tạo duplicate state. Cần thiết vì registration là write operation với side effects (event publish)
- **FR-020**: Full transaction logging — audit compliance và troubleshooting. Cần thiết cho production-grade service
- **FR-021**: Timeout handling cho Kafka — resilience khi messaging infrastructure gặp sự cố
- **FR-022**: Retry mechanism — standard pattern cho distributed system reliability

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Kafka | Event publishing/consuming | Topic: iam.user.registered, iam.permission.changed |
| PostgreSQL | Event store persistence | Append-only event_store table |
| Redis | Cache invalidation | L2 distributed cache |
| Caffeine | Cache invalidation | L1 in-process cache |
| account-service | Downstream consumer | Profile creation on UserRegisteredEvent |
| eventsourcing-utils | CQRS base types | Command, CommandHandler, Query, QueryHandler |
| base-core | Infrastructure auto-config | Redis, security, web starters |

## 6. Assumptions

- ⚠️ Assumption: `eventsourcing-utils` library cung cấp chỉ Command/Query base types — không có event store implementation — cần tự build event store trên PostgreSQL
- ⚠️ Assumption: `account-service` (sibling module) sẽ là consumer chính của UserRegisteredEvent cho profile creation — chưa verify consumer contract
- ⚠️ Assumption: Kafka đã configured với `spring.kafka.bootstrap-servers` — KafkaEventPublisher active khi property present
- ⚠️ Assumption: Event store table sử dụng Snowflake ID từ base-core (BIGINT) cho event_id — consistent với existing ID generation strategy
- ⚠️ Assumption: CloudEvents envelope sẽ wrap existing DomainEvent interface — backward-compatible với current consumers

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 21/25 | FR-011: Khái niệm "read-model projection" chưa rõ scope — projection cho toàn bộ User aggregate hay chỉ registration data? |
| Đầy đủ (Completeness) | 18/25 | FR-009: Event replay chưa xác định rõ boundary — replay toàn bộ events hay chỉ registration events? FR-018: Snapshot interval chưa specify giá trị mặc định |
| Nhất quán (Consistency) | 22/25 | FR-002: Cần resolve duplicate UserRegisteredEvent trước khi implement các FR khác |
| Kiểm thử được (Testability) | 17/25 | FR-006: CloudEvents compliance test criteria chưa rõ ràng; FR-011: Projection correctness verification mechanism chưa define |
| **Tổng** | **78/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -4 | FR-011 | "read-model projections được build từ events" — scope projection chưa rõ, User aggregate có nhiều event types | Clarify projection scope: registration projection vs full User state projection |
| 2 | Completeness | -4 | FR-009 | "replay events từ event store để rebuild aggregate state" — boundary aggregate chưa define | Define aggregate boundaries: User aggregate bao gồm events nào (registered, login, logout, role-change?) |
| 3 | Completeness | -3 | FR-018 | "snapshot sau mỗi N events" — N chưa specify | Specify default N = 100; configurable via property |
| 4 | Consistency | -3 | FR-002 | Hai định nghĩa UserRegisteredEvent tồn tại — eventType naming inconsistent ("user.registered" vs "USER_REGISTERED") | Consolidate vào một file duy nhất |
| 5 | Testability | -4 | FR-006 | "tuân thủ CloudEvents 1.0 spec" — test criteria vague | Define concrete CloudEvents required fields + validation schema |
| 6 | Testability | -4 | FR-011 | "projection trả kết quả chính xác" — correctness criteria chưa rõ | Define projection verification: replay → compare with current state |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Conflict | 🔴 | Duplicate UserRegisteredEvent: `EventPublisher.kt` (eventType=`"user.registered"`) vs `AuthDomainEvents.kt` (eventType=`"USER_REGISTERED"`). RegisterHandler imports từ `AuthDomainEvents.kt`. Port file cũng define UserRegisteredEvent. Hai class cùng tên gây compile conflict hoặc runtime confusion. | FR-002 | Hợp nhất vào port file `EventPublisher.kt` với dot-notation naming; xóa `AuthDomainEvents.kt` duplicate; update imports trong `RegisterHandler` |
| 2 | Risk | 🟡 | Event store + transactional outbox tăng complexity và write latency — mỗi registration ghi vào users table + event_store table + outbox table trong cùng transaction | FR-004, FR-007 | Benchmark write latency; consider async outbox relay với polling interval < 100ms |
| 3 | Risk | 🟡 | Event replay cho User aggregate cần define rõ aggregate boundary — nếu include tất cả auth events (login, logout, role-change, password-change), replay performance có thể chậm | FR-009, FR-018 | Limit User aggregate events scope; implement snapshots (FR-018) để giảm replay time |
| 4 | Missing | 🟡 | Chưa có event upcaster mechanism — khi event schema evolve (v1 → v2), events cũ trong store cần được transform | FR-003 | Design event upcaster registry; upcaster chain transform old versions → current version on replay |

## 9. Open Questions

- **Q1**: Aggregate boundary cho User trong Event Sourcing — User aggregate nên bao gồm những event types nào? Chỉ registration? Hay cả login/logout/role-change/password-change?
- **Q2**: Event replay strategy — có cần support temporal queries (query User state tại thời điểm T)? Hay chỉ cần rebuild current state?
- **Q3**: Consumer contract ownership — ai define schema cho downstream consumers? auth-service (producer) hay mỗi consumer tự define?

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Authorization — Event Sourcing cho User Registration flow

### 10.2 Flow Type

Command — RegisterCommand → RegisterHandler → UserRegisteredEvent → Kafka

### 10.3 Candidate Services
- **auth-service (auth module)**: Keyword `UserRegisteredEvent`, `RegisterHandler`, `RegisterCommand` → Package `auth.application.command` → File `RegisterHandler.kt`, `AuthDomainEvents.kt`
- **auth-service (shared module)**: Keyword `EventPublisher`, `KafkaEventPublisher`, `AuditLogService` → Package `auth.adapter.out.event`, `shared.audit` → File `KafkaEventPublisher.kt`, `AuditLogService.kt`
- **auth-service (shared/config)**: Keyword `KafkaConfig`, `RedisConfig` → Package `shared.config` → File `KafkaConfig.kt`, `RedisConfig.kt`

### Detection Evidence
- Keyword: `UserRegisteredEvent` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`
- Keyword: `EventPublisher` → Module: `auth.application.port.out` → File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
- Keyword: `RegisterHandler` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
- Keyword: `KafkaEventPublisher` → Module: `auth.adapter.out.event` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
- Keyword: `SpringEventPublisher` → Module: `auth.adapter.out.event` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt`

### 10.4 External Integrations

| Integration | Type | Evidence |
|-------------|------|----------|
| Kafka | MQ | `KafkaEventPublisher` → `KafkaTemplate<String, String>` — `src/main/kotlin/.../adapter/out/event/KafkaEventPublisher.kt` |
| Redis | Cache | `RedisConfig` → `RedisTemplate<String, Any>` — `src/main/kotlin/.../shared/config/RedisConfig.kt` |
| Caffeine | Cache | `AbstractTwoTierCache` → `Caffeine.newBuilder()` — `src/main/kotlin/.../shared/cache/AbstractTwoTierCache.kt` |
| PostgreSQL | DB | `UserPersistenceAdapter` → JPA Repository — `src/main/kotlin/.../adapter/out/persistence/UserPersistenceAdapter.kt` |
| base-core | Library | `base-web-starter`, `base-data-starter`, `base-security-starter` — `build.gradle.kts` |
| eventsourcing-utils | Library | `Command`, `CommandHandler` base types — `build.gradle.kts` dependency `com.ntt:eventsourcing-utils:0.0.1-SNAPSHOT` |

### 10.5 Required Modules

- `auth.application.command` — RegisterCommand, RegisterHandler, AuthDomainEvents
- `auth.application.port.out` — EventPublisher, DomainEvent, UserRegisteredEvent
- `auth.adapter.out.event` — KafkaEventPublisher, SpringEventPublisher
- `auth.adapter.in.kafka` — PermissionChangedConsumer (example consumer pattern)
- `shared.config` — KafkaConfig, RedisConfig
- `shared.cache` — AbstractTwoTierCache
- `shared.audit` — AuditLogService, AuditAction
- `shared.exception` — AuthException, AuthErrorCode
- `auth.domain.model` — User, UserStatus, AuthToken
- `auth.adapter.out.persistence` — UserPersistenceAdapter, event_store (NEW)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi POST /api/auth/register với RegisterRequestDto | CqrsAuthController |
| 2 | CqrsAuthController | Tạo RegisterCommand từ request DTO | CqrsAuthController |
| 3 | RegisterHandler | Validate uniqueness (username, email) | UserPort |
| 4 | RegisterHandler | Validate domain exists | DomainPort |
| 5 | RegisterHandler | Create User domain object | User domain model |
| 6 | RegisterHandler | Persist user via UserPort.save() | UserPersistenceAdapter → PostgreSQL |
| 7 | RegisterHandler | **[NEW]** Persist UserRegisteredEvent vào event_store table (trong cùng transaction) | EventStore → PostgreSQL |
| 8 | RegisterHandler | **[NEW]** Persist event vào outbox table (trong cùng transaction) | OutboxTable → PostgreSQL |
| 9 | RegisterHandler | Publish UserRegisteredEvent via EventPublisher | KafkaEventPublisher |
| 10 | KafkaEventPublisher | Serialize event → JSON, send to Kafka topic `iam.user.registered` | Kafka |
| 11 | **[NEW]** Outbox Relay | Poll outbox table → publish unpublished events → mark as published | OutboxRelay → Kafka |
| 12 | Downstream consumers | Consume UserRegisteredEvent → create profile, send welcome email, assign roles | account-service, notification-service |
| 13 | RegisterHandler | Return RegisterResult.Success(authToken, promotionResult) | CqrsAuthController |
| 14 | CqrsAuthController | Return AuthResponse (201 Created) | Client |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Idea: enriched event payload | TBD | `UserRegisteredEvent` (EventPublisher.kt), `RegisterHandler.kt` | [MODIFY] Mapped |
| FR-002 | Idea: consolidate duplicate events | TBD | `AuthDomainEvents.kt` (DELETE), `EventPublisher.kt` (MODIFY) | [MODIFY] Mapped |
| FR-003 | Idea: event schema versioning | TBD | `DomainEvent` (EventPublisher.kt) — add schemaVersion field | [MODIFY] Mapped |
| FR-004 | Idea: event store persistence | TBD | NEW `EventStoreEntity`, `EventStoreRepository` | [ADD] Mapped |
| FR-005 | Idea: correlation ID | TBD | `DomainEvent` interface — add correlationId | [MODIFY] Mapped |
| FR-006 | Idea: CloudEvents envelope | TBD | NEW `CloudEventEnvelope` wrapper | [ADD] Mapped |
| FR-007 | Idea: transactional outbox | TBD | NEW `OutboxEntity`, `OutboxRepository`, `OutboxRelay` | [ADD] Mapped |
| FR-008 | Idea: idempotent consumer | TBD | `DomainEvent` interface — eventId as dedup key | [MODIFY] Mapped |
| FR-009 | Idea: event replay | TBD | NEW `EventReplayService` | [ADD] Mapped |
| FR-010 | Idea: Kafka topic design | TBD | `KafkaEventPublisher.kt` — topic naming + partition key | [MODIFY] Mapped |
| FR-011 | Idea: CQRS read-model | TBD | NEW `UserProjection`, `UserProjectionHandler` | [ADD] Mapped |
| FR-012 | Idea: consumer contracts | TBD | NEW consumer contract documentation | [ADD] Pending |
| FR-013 | Idea: audit trail | TBD | `AuditLogService.kt` — integration with event store | [REUSE] Mapped |
| FR-014 | Idea: event metadata | TBD | `DomainEvent` interface — add metadata fields | [MODIFY] Mapped |
| FR-015 | Idea: DLT | TBD | `KafkaConfig.kt` — DLT already exists | [REUSE] Mapped |
| FR-016 | Idea: cache invalidation | TBD | `AbstractTwoTierCache.kt`, `MultiTierPermissionCache.kt` | [REUSE] Mapped |
| FR-017 | Idea: eventsourcing-utils | TBD | `RegisterCommand.kt`, `RegisterHandler.kt` | [REUSE] Mapped |
| FR-018 | Idea: event snapshot | TBD | NEW `SnapshotEntity`, `SnapshotRepository` | [ADD] Mapped |
| FR-019 | Enriched: idempotency | TBD | `IdempotencyFilter.kt` — already exists | [REUSE] Mapped |
| FR-020 | Enriched: transaction logging | TBD | `RegisterHandler.kt` — add structured logs | [MODIFY] Mapped |
| FR-021 | Enriched: timeout handling | TBD | `KafkaEventPublisher.kt` — add timeout config | [MODIFY] Mapped |
| FR-022 | Enriched: retry mechanism | TBD | `KafkaEventPublisher.kt` — retry already exists (@Retryable) | [REUSE] Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

Tính năng này có **độ phức tạp cao** — kết hợp Event Sourcing patterns (event store, outbox, replay, snapshots) với existing CQRS infrastructure. Codebase hiện tại đã có nền tảng tốt:
- CQRS pattern đã implement (Command/CommandHandler from eventsourcing-utils)
- Kafka integration đã hoạt động (KafkaEventPublisher với retry)
- DomainEvent interface đã defined
- Hexagonal architecture rõ ràng (port/adapter)

**Rủi ro chính**: Duplicate `UserRegisteredEvent` (2 definitions) cần resolve trước khi implement bất kỳ FR nào khác — đây là blocker.

**Lưu ý**: Feature research đã hoàn thành tại `openspec/research/user_registration_event/` với 6 files research bao gồm business analysis, technical spec, comparison analysis, open source findings. Research confirms:
- Event store nên dùng PostgreSQL (append-only table) thay vì dedicated EventStoreDB — phù hợp với existing tech stack
- Transactional outbox pattern là approach đúng cho reliability
- CloudEvents envelope cung cấp standardization tốt

### Related Features / Precedents

- **auth-core-features** (archive: `openspec/changes/archive/2026-08-20-auth-core-features/`): Đã implement MFA, SSO, RBAC patterns. Có thể tham khảo patterns cho event handling
- **erp-iam-system** (archive: `openspec/changes/archive/2026-08-24-erp-iam-system/`): Có IAM event patterns
- **architecture-optimization** (archive: `openspec/changes/archive/2026-08-05-architecture-optimization/`): Codebase restructuring — hexagonal architecture patterns

### Integration Notes

- **Kafka**: Đã configured với `KafkaConfig` (DLQ support), `KafkaEventPublisher` (retry 3 attempts, exponential backoff). Topic naming hiện tại lấy từ `event.eventType` trực tiếp — cần normalize sang `iam.user.registered` convention
- **PostgreSQL**: Flyway migrations V1-V10 đã tồn tại — event_store migration sẽ là V11
- **Redis**: RedisConfig + AbstractTwoTierCache đã setup — cache invalidation on registration events có thể reuse existing infrastructure
- **base-core**: Provides SnowflakeID generation, BaseControllerAdvice, ErrorCodeBase — event_store table sẽ dùng Snowflake ID cho event_id
- **account-service**: Sibling module — sẽ là downstream consumer chính, cần define consumer contract

### Suggested Approach

1. **Phase 1 — Consolidate events**: Resolve duplicate UserRegisteredEvent; enrich payload; add CloudEvents envelope fields to DomainEvent interface
2. **Phase 2 — Event store**: Design event_store table (Flyway V11); implement EventStoreRepository; integrate with RegisterHandler transaction
3. **Phase 3 — Transactional outbox**: Design outbox table; implement OutboxRelay (polling-based); replace fire-and-forget publish with outbox-based publish
4. **Phase 4 — Consumer patterns**: Define consumer contracts; implement idempotent consumer pattern (dedup by eventId)
5. **Phase 5 — Projections & Replay**: Implement UserProjection; build replay service; add snapshot support

**Recommended base classes**:
- `DomainEvent` interface (existing) — extend with metadata fields
- `AbstractTwoTierCache` (existing) — reuse for projection caching
- `VersionedAuditableEntity` (existing) — for EventStoreEntity
- `SnowflakePersistentAuditableEntity` (base-core) — for OutboxEntity

### Context from Confluence Images

N/A — source là user idea, không có Confluence images.
