# Impact Analysis: erp-iam-system

_Generated: 2026-08-24_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code trong auth-service. BẮT BUỘC `file:///` link + line range.
> ⚠️ system-admin-service, account-service: NEWBUILD modules — không có existing code trong repo này.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | L1-48 | Domain event port — cần thêm new event types cho IAM lifecycle |
| 2 | [SpringEventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt) | L1-30 | Event adapter — cần migrate sang Kafka adapter (Phase 4) |
| 3 | [AuditLogService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | L1-124 | Audit — cần thêm persist to DB + new AuditAction enums |
| 4 | [AuthErrorCode.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt) | Full | Error codes — cần thêm ranges cho new modules (AUTH_050+) |
| 5 | [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Full | Security filter chain — cần thêm internal API endpoints |
| 6 | [HttpClientConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt) | Full | HTTP client — cần thêm timeout + circuit breaker config |
| 7 | [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Full | SSO — cần enhance auto-provision flow |
| 8 | [AccountLifecycleService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt) | Full | Lifecycle — cần GDPR enhancements + inter-service events |

---

## 2. Call Tree — LOGIC CẦN SỬA

### `EventPublisher.publish()` — Event Flow

⟶ EventPublisher.publish(event: DomainEvent)
├── UserRegisteredEvent → RegisterHandler.handle()
│   └── SpringEventPublisher.publish()  // ← currently logs only, Phase 4 → KafkaPublisher
├── SsoProvisionedEvent → SsoAdapter.autoProvision()
│   └── SpringEventPublisher.publish()  // ← triggers account-service profile creation
├── PermissionChangedEvent → RbacEngine (implied)
│   └── PermissionChangedConsumer.consume()  // ← Kafka listener already exists
└── [NEW] AccountDeactivatedEvent → AccountLifecycleService.deactivate()
    └── [NEW] KafkaEventPublisher.publish()  // ← Phase 4: invalidate tokens cross-service

### `AuditLogService.logEvent()` — Audit Flow

⟶ AuditLogService.logEvent(userId, action, entityType, entityId, details)
├── getCurrentRequest() → HttpServletRequest (nullable)
├── getClientIp(request) → X-Forwarded-For or remoteAddr
├── log.info("AUDIT action={} ...") → structured JSON log
└── // TODO: persist to audit_log table  // ← MUST implement for FR-015

**Callers (8 services):**
- AuthService.kt → LOGIN_SUCCESS, LOGIN_FAILED, ACCOUNT_LOCKED
- MfaService.kt → MFA_SETUP, MFA_VERIFY_SUCCESS, MFA_VERIFY_FAILED
- SsoAdapter.kt → SSO_LOGIN, SSO_LINK, SSO_UNLINK
- MfaRateLimitService.kt → MFA_OTP_LOCKED, MFA_LOGIN_LOCKED
- PasswordPolicyService.kt → PASSWORD_CHANGED
- AccountLifecycleService.kt → ACCOUNT_DEACTIVATED, DELETION_REQUESTED, etc.
- RevokeSessionsHandler.kt → SESSION_REVOKED
- [NEW] system-admin-service AuditAspect → ADMIN_CREATE, ADMIN_UPDATE, ADMIN_DELETE

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (8 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `RegisterHandler.kt` | [RegisterHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | Inject EventPublisher, gọi .publish(UserRegisteredEvent) |
| 2 | `SsoAdapter.kt` | [SsoAdapter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Inject EventPublisher + AuditLogService |
| 3 | `AuthService.kt` | [AuthService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | Inject AuditLogService |
| 4 | `MfaService.kt` | [MfaService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Inject AuditLogService |
| 5 | `MfaRateLimitService.kt` | [MfaRateLimitService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt) | Inject AuditLogService |
| 6 | `PasswordPolicyService.kt` | [PasswordPolicyService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | Inject AuditLogService |
| 7 | `RevokeSessionsHandler.kt` | [RevokeSessionsHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt) | Inject AuditLogService |
| 8 | `AccountLifecycleService.kt` | [AccountLifecycleService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt) | Inject AuditLogService + EventPublisher (new) |

### 🟡 Indirect Impact — auth-service (5 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `PermissionChangedConsumer.kt` | [Consumer](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt) | Kafka consumer — receives PermissionChangedEvent |
| 2 | `MultiTierPermissionCache.kt` | [Cache](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt) | Cache invalidation triggered by permission events |
| 3 | `RedisConfig.kt` | [Redis](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt) | Redis template — may need new serialization configs |
| 4 | `LoginSessionService.kt` | [Session](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt) | Session termination on account deactivation |
| 5 | `DomainLookupService.kt` | [Domain](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/DomainLookupService.kt) | Domain config — FR-016 extends with branding, login page |

### 🟠 Cross-service Impact (3 services)

| # | File | Link | Protocol | Cách sử dụng |
|---|------|------|----------|-------------|
| 1 | account-service (NEWBUILD) | N/A | Kafka | Consumes `iam.user.registered`, `iam.user.sso_provisioned` → profile creation |
| 2 | system-admin-service (NEWBUILD) | N/A | REST + Redis | Reads user roles via internal REST API; writes rate limit config to Redis |
| 3 | API Gateway (external) | N/A | Redis | Reads Bucket4j rate limit config from Redis for API key validation |

### 🟢 Shared Utilities (4 files)

| # | File | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `SnowflakePersistentAuditableEntity` | base-core library | Entity base — all new entities extend this |
| 2 | `BaseControllerAdvice` | base-core library | Exception handling — new services inherit |
| 3 | `DatabaseMessageSource` | [I18n](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt) | I18n messages — reuse pattern for new error codes |
| 4 | `VersionedAuditableEntity` | [Versioned](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt) | Optimistic locking — reuse for concurrent-updated entities |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Event Publisher Port | `EventPublisher.kt` (L1-48) | 100% | REUSE | 🟢 (2 callers) | Add new event types, keep interface |
| Audit Logging | `AuditLogService.kt` (L1-124) | 80% | EXTEND | 🟡 (8 callers) | Add persistence, new AuditAction enums |
| Entity Base Class | `SnowflakePersistentAuditableEntity` (base-core) | 100% | REUSE | 🟢 | All new entities extend |
| Versioned Entity | `VersionedAuditableEntity.kt` | 100% | REUSE | 🟢 | Use for concurrent-write entities |
| Error Code Pattern | `AuthErrorCode.kt` | 100% | REUSE | 🟢 | Add new ranges (050+) |
| Cache Pattern | `MultiTierPermissionCache.kt` | 80% | EXTRACT | 🟡 (3 callers) | Abstract two-tier cache pattern for menus, sessions |
| HTTP Client Config | `HttpClientConfig.kt` | 70% | EXTEND | 🟢 (2 callers) | Add timeout + circuit breaker |
| Clean Architecture Structure | `adapter/in/web`, `adapter/out/persistence`, `application` | 100% | REUSE | 🟢 | Replicate package structure in new services |
| PBAC PolicyCondition | `PolicyEntities.kt` | 80% | EXTRACT | 🟢 (1 caller) | Extract condition evaluator for workflow DSL |
| Tree Data Structure | NOT FOUND | 0% | NEW | 🟢 | Create TreeEntity for menu/department hierarchy |
| Organization Module | NOT FOUND | 0% | NEW | 🟢 | Full NEWBUILD |
| Workflow Engine | NOT FOUND | 0% | NEW | 🟢 | Full NEWBUILD |
| Profile CRUD | NOT FOUND (stub only) | 10% | NEW | 🟢 | Full NEWBUILD on account-service |
| Device Management | NOT FOUND | 0% | NEW | 🟢 | Full NEWBUILD on account-service |

### EXTRACT Details

#### E1: Two-Tier Cache Abstraction

**Source:** `MultiTierPermissionCache.kt` — L1 Caffeine + L2 Redis orchestrator
**Target:** `AbstractTwoTierCache<K, V>` — `shared/cache/`
**Callers found:** 3 files
- `MultiTierPermissionCache.kt` → will extend abstract base
- [NEW] `MenuPermissionCacheAdapter` → will extend abstract base
- [NEW] `SessionCacheAdapter` → will extend abstract base

**Impact level:** 🟡 Medium (backward-compatible — current class becomes child)
**Breaking changes:** Không — backward compatible via inheritance
**Migration plan:** Extract abstract base → current impl becomes `PermissionTwoTierCache extends AbstractTwoTierCache`

#### E2: PBAC Condition Evaluator for Workflow DSL

**Source:** `PolicyEvaluator.kt` — JSONB condition evaluation logic
**Target:** `ConditionEvaluator` — `shared/condition/` or `base-core common-expression`
**Callers found:** 1 file (`PolicyEvaluator.kt`)

**Impact level:** 🟢 Low
**Breaking changes:** Không
**Migration plan:** Extract generic evaluator → PolicyEvaluator delegates to it → WorkflowEngine reuses it

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `EventPublisher` | Interface (port) | `publish(event: DomainEvent)` | Outbound port for domain events |
| `AuditLogService` | @Service (injected) | `logEvent()`, `logEncryptedAction()` | Structured audit logger |
| `SnowflakePersistentAuditableEntity` | @MappedSuperclass (extends) | Auto ID, audit fields | base-core library |
| `BaseControllerAdvice` | @RestControllerAdvice (extends) | Exception → ProblemDetail | base-core library |
| `StringRedisTemplate` | Spring Data Redis | `opsForValue()`, `opsForHash()` | Redis cache/state |
| `DatabaseMessageSource` | @Component | `getMessage()` | I18n message resolution |

### Config Keys

| Key | Source | Ví dụ value | Nơi dùng |
|-----|--------|------------|---------|
| `spring.data.redis.*` | application.yml | `localhost:6379` | RedisConfig |
| `spring.kafka.bootstrap-servers` | application.yml | `localhost:9092` | KafkaConfig (Phase 4) |
| `auth.jwt.private-key-path` | SecurityProperties | RSA private key | JwtService |
| `auth.mfa.otp-ttl-seconds` | application.yml | `300` | OtpService |
| `auth.session.max-concurrent` | application.yml | `5` | SessionPolicyService |

### Error Codes Thrown

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| `AUTH_001` | Invalid credentials | AuthService.authenticate() |
| `AUTH_002` | Account locked | AuthService.authenticate() |
| `AUTH_003` | Token expired | JwtService.validateToken() |
| `AUTH_005` | Resource not found | Multiple services |
| `AUTH_011-013` | MFA failures | MfaService |
| `AUTH_019-020` | Rate limits | MfaRateLimitService, LoginRateLimitService |
| `AUTH_050+` | [NEW] system-admin errors | To be defined |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| MenuTreeResponse | N/A | 0% | NEW |
| DepartmentTreeResponse | N/A | 0% | NEW |
| ApiPartnerResponse | N/A | 0% | NEW |
| ProfileResponse | N/A | 0% | NEW |
| WorkflowInstanceResponse | N/A | 0% | NEW |
| AuditLogResponse | N/A | 0% | NEW |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| EventPublisher.publish() | `fun publish(event: DomainEvent)` | grep_search | ✅ |
| AuditLogService.logEvent() | `fun logEvent(userId, action, entityType, entityId, details)` | view_file | ✅ |
| SnowflakePersistentAuditableEntity | `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity` | grep_search | ✅ |
| BaseControllerAdvice | `com.ntt.basecore.domain.web.BaseControllerAdvice` | grep_search | ✅ |
