# Impact Analysis: user-registration-event

_Generated: 2025-08-21_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [RegisterHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | L69-73 | Replace `eventPublisher.publish()` with `eventService.record()` — transactional event sourcing |
| 2 | [RegisterCommand.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt) | L9-18 | Add `ipAddress`, `userAgent`, `correlationId` fields |
| 3 | [CqrsAuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | L73-87 | Pass ipAddress/userAgent/correlationId to RegisterCommand; add HttpServletRequest param |
| 4 | [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | L19-25 | Remove duplicate `UserRegisteredEvent` class |
| 5 | [AuthDomainEvents.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt) | L8-13 | Remove duplicate `UserRegisteredEvent`; keep `UserLoggedInEvent`, `SessionRevokedEvent` |
| 6 | [KafkaEventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt) | L47 | Fix topic mismatch — ensure `iam.` prefix applied; add partition key support |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. Ghi annotation `// ←` ở điểm quan trọng.

#### `RegisterHandler.handle(command)` (L42-97)

```
⟶ handle(command: RegisterCommand)
├── userPort.existsByUsername(command.username)  // ← validation, no change
├── userPort.existsByEmail(command.email)        // ← validation, no change
├── domainPort.findByCodeAndActive(command.domainCode)  // ← validation, no change
├── tokenGenerator.encodePassword(command.password)     // ← domain, no change
├── User(...) → domain model creation                   // ← no change
├── userPort.save(user) → savedUser                     // ← persistence, no change
├── eventPublisher.publish(UserRegisteredEvent(...))     // ← 🔴 REMOVE THIS
│   └── KafkaEventPublisher.publish(event)
│       └── kafkaTemplate.send(topic=event.eventType, payload)  // ← topic bug here
├── [NEW] eventService.record(                           // ← 🟢 REPLACE WITH THIS
│       aggregateType="User",
│       aggregateId=savedUser.id.value,
│       event=UserRegisteredEvent(enriched),
│       topic="iam.user.registered",
│       partitionKey=savedUser.id.value.toString(),
│       correlationId=command.correlationId)
│   ├── EventEnvelope creation (metadata, correlationId)
│   ├── eventStorePort.append() → INSERT event_store     // ← same TX
│   └── outboxPort.insert() → INSERT event_outbox        // ← same TX
├── tokenGenerator.generateAuthResponse(savedUser, domainCode)  // ← no change
└── sessionPromotionService.promoteSession(...)                  // ← no change (optional)
```

#### `CqrsAuthController.register()` (L73-107)

```
⟶ register(@RequestBody request, httpRequest: HttpServletRequest)  // ← ADD httpRequest param
├── extractAnonymousTokenJti(request.anonymousToken)  // ← no change
├── RegisterCommand(
│       ...existing fields...,
│       ipAddress = extractClientIp(httpRequest),           // ← NEW
│       userAgent = httpRequest.getHeader("User-Agent"),    // ← NEW
│       correlationId = httpRequest.getHeader("X-Correlation-ID")  // ← NEW
│   )
├── registerHandler.handle(command)                // ← no change
└── ResponseEntity.status(201).body(response)      // ← no change
```

#### `KafkaEventPublisher.publish(event)` (L46-62)

```
⟶ publish(event: DomainEvent)
├── val topic = event.eventType          // ← 🔴 BUG: "user.registered" not "iam.user.registered"
│   [FIX] val topic = if (event.eventType.startsWith("iam.")) event.eventType
│                     else TOPIC_PREFIX + event.eventType
├── objectMapper.writeValueAsString(event)  // ← no change
└── kafkaTemplate.send(topic, payload)      // ← add partition key in future
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (6 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `RegisterHandler.kt` | [RegisterHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | Imports `UserRegisteredEvent` from `EventPublisher.kt` port; calls `eventPublisher.publish()`. MUST update import + replace with eventService.record() |
| 2 | `CqrsAuthController.kt` | [CqrsAuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | Creates `RegisterCommand` — MUST add ipAddress/userAgent/correlationId params + HttpServletRequest |
| 3 | `KafkaEventPublisher.kt` | [KafkaEventPublisher](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt) | `publish(event)` uses `event.eventType` as topic — MUST fix prefix logic |
| 4 | `EventPublisher.kt` | [EventPublisher](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | Contains duplicate `UserRegisteredEvent` (L19-25) — MUST remove |
| 5 | `AuthDomainEvents.kt` | [AuthDomainEvents](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt) | Contains duplicate `UserRegisteredEvent` (L8-13) — MUST remove |
| 6 | `RegisterCommand.kt` | [RegisterCommand](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt) | Command DTO — MUST add new fields |

### 🟡 Indirect Impact — auth-service (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `SpringEventPublisher.kt` | [SpringEventPublisher](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt) | Fallback EventPublisher — logs only. Still used for non-outbox events. No change needed but verify `DomainEvent` import still valid |
| 2 | `LoginHandler.kt` | [LoginHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | Uses `UserLoggedInEvent` from `AuthDomainEvents.kt` — MUST verify import still valid after `UserRegisteredEvent` removal |
| 3 | `AuditLogService.kt` | [AuditLogService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | Uses `EventPublisher` port + `AuditEvent` — no change needed, but verify `DomainEvent` interface unchanged |

### 🟠 Cross-service Impact (1 file)

| # | File | Link | Protocol | Cách sử dụng |
|---|------|------|----------|-------------|
| 1 | `ProfileKafkaListener` | N/A (account-service) | Kafka | Subscribes to `iam.user.registered` — currently NOT receiving events (topic mismatch). After fix: WILL receive enriched payload. Backward-compatible via `@JsonIgnoreProperties(ignoreUnknown = true)` |

### 🟢 Shared Utilities (3 files)

| # | File | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `VersionedAuditableEntity.kt` | [VersionedAuditableEntity](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt) | Base entity for optimistic locking — NOT used by event_store entities (no updates) |
| 2 | `SnowflakePersistentAuditableEntity` | base-core library | Base entity with Snowflake ID — used by all new entities |
| 3 | `SessionCleanupScheduler.kt` | [SessionCleanupScheduler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt) | Reference pattern for `OutboxPoller` — @Scheduled + @Transactional |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Event publishing | [KafkaEventPublisher.publish():L46-62](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt#L46) | 60% | MODIFY | 🟢 Low (1 caller — OutboxPoller reuses KafkaTemplate directly) | Fix topic prefix; KafkaEventPublisher still used for non-outbox events |
| Scheduled job pattern | [SessionCleanupScheduler:L33-56](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt#L33) | 80% | REUSE | 🟢 Low (pattern reuse, not code extraction) | OutboxPoller follows same @Scheduled + @Transactional pattern, uses fixedDelay instead of cron |
| Base entity (Snowflake ID) | SnowflakePersistentAuditableEntity (base-core) | 100% | REUSE | 🟢 Low | All new entities extend this |
| DomainEvent interface | [EventPublisher.kt:L12-14](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt#L12) | 100% | REUSE | 🟢 Low | New UserRegisteredEvent implements existing DomainEvent interface |
| Error code pattern | [AuthErrorCode](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt) | 100% | REUSE | 🟢 Low | Add AUTH_050-053 following existing pattern |
| Kafka DLQ config | [KafkaConfig:L29-50](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt#L29) | 100% | REUSE | 🟢 Low | Existing DLQ config applies to new topics automatically |
| Idempotency filter | [IdempotencyFilter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt) | 100% | REUSE | 🟢 Low | Already handles registration command idempotency (FR-019) |

> No EXTRACT candidates identified — all reuse is either pattern-based (REUSE) or modification (MODIFY).
> All matches are 🟢 Low impact.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `EventPublisher` | Interface (injected) | `publish(event: DomainEvent)` | Existing — still used for non-outbox events (AuditEvent, etc.) |
| `UserPort` | Interface (injected) | `save(user)`, `existsByUsername()`, `existsByEmail()` | Existing — no change |
| `DomainPort` | Interface (injected) | `findByCodeAndActive(code)` | Existing — no change |
| `TokenGenerator` | Interface (injected) | `encodePassword()`, `generateAuthResponse()` | Existing — no change |
| `SessionPromotionService` | Class (injected) | `promoteSession()` | Existing — no change |
| `EventStorePort` | Interface (NEW) | `append()`, `findByAggregate()`, `getNextSequenceNumber()` | New port for event store |
| `OutboxPort` | Interface (NEW) | `insert()`, `findPendingForUpdate()`, `markPublished()` | New port for outbox |
| `EventService` | Class (NEW) | `record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)` | New intermediary — injected into RegisterHandler |
| `KafkaTemplate<String, String>` | Spring Bean | `send(topic, key, data)` | Existing — used by OutboxPoller directly |
| `ObjectMapper` | Spring Bean | `writeValueAsString()` | Existing — used by EventService for JSON serialization |
| `SnowflakePersistentAuditableEntity` | Base class (base-core) | Snowflake ID auto-generation | New entities extend this |

### Config Keys

| Key | Source | Ví dụ value | Nơi dùng |
|-----|--------|-----------|---------|
| `app.event.source` | application.yml | `auth-service` | EventService — EventEnvelope.source |
| `app.event.outbox.poll-interval-ms` | application.yml | `100` | OutboxPoller — @Scheduled fixedDelay |
| `app.event.outbox.batch-size` | application.yml | `50` | OutboxPoller — SELECT LIMIT |
| `app.event.outbox.max-retries` | application.yml | `3` | OutboxPoller — max retry before FAILED |
| `app.event.outbox.kafka-timeout-ms` | application.yml | `5000` | OutboxPoller — Kafka send timeout |
| `spring.kafka.bootstrap-servers` | application.yml | `localhost:9092` | KafkaEventPublisher, OutboxPoller activation |

### Error Codes Thrown

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| `AUTH_006` (DUPLICATE_RESOURCE) | Username/email already exists | [RegisterHandler.handle():L44-47](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt#L44) |
| `AUTH_005` (RESOURCE_NOT_FOUND) | Domain not found | [RegisterHandler.handle():L51](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt#L51) |
| `AUTH_050` (EVENT_STORE_PERSIST_FAILED) | Event store write failure | EventService.record() — NEW |
| `AUTH_051` (OUTBOX_PUBLISH_FAILED) | Kafka publish failure in outbox relay | OutboxPoller — NEW (logged, not thrown to user) |
| `AUTH_052` (EVENT_NOT_FOUND) | Event query returns empty | EventStoreController — NEW |
| `AUTH_053` (OUTBOX_MAX_RETRIES_EXCEEDED) | Outbox entry exceeded 3 retries | OutboxPoller — NEW (logged, not thrown to user) |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| EventEnvelope<T> | N/A | 0% | NEW — no existing envelope |
| UserRegisteredEvent (enriched) | UserRegisteredEvent (minimal) | 30% | NEW — replace existing |
| OutboxProperties | N/A | 0% | NEW — config properties class |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `CommandHandler<C,R>.handle(command)` | `abstract fun handle(command: C): R` | eventsourcing-utils (base-core) | ✅ Verified — RegisterHandler extends this |
| `SnowflakePersistentAuditableEntity` | `open class ... : AbstractPersistableEntity<Long>` | base-core | ✅ Verified — VersionedAuditableEntity extends this |
| `KafkaTemplate.send(topic, key, data)` | `fun send(topic: String, key: K, data: V): CompletableFuture<SendResult>` | spring-kafka | ✅ Verified — existing usage in KafkaEventPublisher |
| `DomainEvent.eventType` | `val eventType: String` (interface property) | EventPublisher.kt L13-14 | ✅ Verified — all event types implement this |
