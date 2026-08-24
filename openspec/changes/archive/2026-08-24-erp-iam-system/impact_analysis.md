# Impact Analysis: erp-iam-system

_Generated: 2025-07-15_
_[CHANGED] Updated from archived version (2026-08-24) — reclassified NEWBUILD → EXTEND_

---

## 1. Core Files — NƠI SỬA

> Liệt kê files CẦN MODIFY code trong auth-service (primary scope). account-service và system-admin-service files ALSO listed.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [MfaService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Full (~340 lines) | MFA orchestrator — cần thêm recovery codes management |
| 2 | [MfaController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | Full | MFA endpoints — cần thêm recovery code endpoints |
| 3 | [EventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt) | Full (~48 lines) | Domain event port — thêm new event types |
| 4 | [SpringEventPublisher.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/SpringEventPublisher.kt) | Full (~30 lines) | Event adapter — delegate to KafkaEventPublisher |
| 5 | [AuditLogService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | Full (~124 lines) | Audit — implement DB persistence (replace TODO) |
| 6 | [AuthErrorCode.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt) | Full | Error codes — thêm AUTH_050–053 |
| 7 | [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Full | Security filter chain — thêm internal API endpoints |
| 8 | [HttpClientConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt) | Full | HTTP client — thêm timeout + circuit breaker config |
| 9 | [ServiceTokenService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/ServiceTokenService.kt) | Full | Service JWT — enhance for cross-service validation |
| 10 | [LoginSessionService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt) | Full | Session management — concurrent session enforcement |
| 11 | [SessionPolicyService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt) | Full | Session policy — max concurrent config |
| 12 | [DomainLookupService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/DomainLookupService.kt) | Full | Domain config — enhance with branding |
| 13 | [AccountLifecycleService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt) | Full | Lifecycle — GDPR enhancements + cross-service events |
| 14 | [SsoAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Full | SSO — enhance auto-provision event |
| 15 | [LoginHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/a→ system-admin invalidates menu cache
├── [NEW] AccountDeactivatedEvent → auth invalidates tokens/sessions
├── [NEW] AccountDeletedEvent → auth purges user data
└── [NEW] OrgStructureChangedEvent → auth updates user metadata

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (10 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `RegisterHandler.kt` | [RegisterHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | Inject EventPublisher, gọi .publish(UserRegisteredEvent) |
| 2 | `SsoAdapter.kt` | [SsoAdapter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | Inject EventPublisher + AuditLogService |
| 3 | `AuthService.kt` | [AuthService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | Inject AuditLogService |
| 4 | `MfaService.kt` | [MfaService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Inject AuditLogService — target of MODIFY |
| 5 | `MfaRateLimitService.kt` | [MfaRateLimitService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt) | Inject AuditLogService |
| 6 | `PasswordPolicyService.kt` | [PasswordPolicyService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | Inject AuditLogService |
| 7 | `RevokeSessionsHandler.kt` | [RevokeSessionsHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt) | Inject AuditLogService |
| 8 | `AccountLifecycleService.kt` | [AccountLifecycleService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt) | Inject AuditLogService + EventPublisher (new) |
| 9 | `SessionPromotionService.kt` | [SessionPromotionService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | MFA recovery code verify → promotion flow |
| 10 | `LoginHandler.kt` | [LoginHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | Trusted device MFA skip integration |

### 🟡 Indirect Impact — auth-service (6 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `PermissionChangedConsumer.kt` | [Consumer](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt) | Kafka consumer — receives PermissionChangedEvent |
| 2 | `MultiTierPermissionCache.kt` | [Cache](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt) | Cache invalidation triggered by permission events |
| 3 | `RedisConfig.kt` | [Redis](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt) | Redis template — may need new serialization configs |
| 4 | `HttpSsoGateway.kt` | [SSO](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt) | Circuit breaker annotation needed |
| 5 | `HttpCaptchaGateway.kt` | [Captcha](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt) | Circuit breaker annotation needed |
| 6 | `AbstractTwoTierCache.kt` | [Cache](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt) | Already extracted — reuse for menu cache in system-admin |

### 🟠 Cross-service Impact (3 services)

| # | File | Link | Protocol | Cách sử dụng |
|---|------|------|----------|-------------|
| 1 | account-service (EXTEND) | `ProfileService.kt`, `DeviceService.kt` | Kafka + REST | Consumes Kafka events; exposes internal REST for device trust check |
| 2 | system-admin-service (EXTEND) | `MenuPermissionService.kt`, `ApiPartnerService.kt`, `WorkflowEngine.kt` | REST + Redis | Reads user roles via internal REST; writes rate limit to Redis |
| 3 | API Gateway (external) | N/A | Redis | Reads Bucket4j rate limit config from Redis |

### 🟢 Shared Utilities (5 files)

| # | File | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `SnowflakePersistentAuditableEntity` | base-core library | Entity base — all new entities extend this |
| 2 | `BaseControllerAdvice` | base-core library | Exception handling — all services |
| 3 | `DatabaseMessageSource` | [I18n](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt) | I18n messages for error codes |
| 4 | `VersionedAuditableEntity` | [Versioned](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt) | Optimistic locking — concurrent-updated entities |
| 5 | `IdempotencyFilter` | [Filter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt) | Replicate pattern to account + system-admin services |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Event Publisher Port | `EventPublisher.kt` | 100% | REUSE | 🟢 (2 callers) | Add new event types, keep interface |
| Audit Logging | `AuditLogService.kt` | 80% | EXTEND | 🟡 (8 callers) | Add DB persistence, masking — backward compatible |
| Entity Base Class | `SnowflakePersistentAuditableEntity` (base-core) | 100% | REUSE | 🟢 | All entities extend |
| Versioned Entity | `VersionedAuditableEntity.kt` | 100% | REUSE | 🟢 | Concurrent-write entities |
| Error Code Pattern | `AuthErrorCode.kt` | 100% | REUSE | 🟢 | Add new ranges (050+) |
| Two-Tier Cache | `AbstractTwoTierCache.kt` | 100% | REUSE | 🟢 | Already extracted — reuse for menu cache |
| HTTP Client Config | `HttpClientConfig.kt` | 70% | EXTEND | 🟢 (2 callers) | Add timeout + circuit breaker |
| Clean Architecture | `adapter/in/web`, `adapter/out/persistence`, `application` | 100% | REUSE | 🟢 | Same structure in all services |
| PBAC PolicyCondition | `PolicyEntities.kt` | 80% | REUSE | 🟢 (1 caller) | Reuse condition JSON format for workflow DSL |
| Idempotency Filter | `IdempotencyFilter.kt` | 100% | REUSE | 🟢 | Replicate to account + system-admin services |
| Tree Data Structure | `TreeEntity.kt` (system-admin) | 100% | REUSE | 🟢 | Already exists — used by menu + department |
| TreeBuilder Utility | `TreeBuilder.kt` (system-admin) | 100% | REUSE | 🟢 | buildTree, detectCycle, calculateTreePath |
| Service Token | `ServiceTokenService.kt` | 80% | EXTEND | 🟢 (1 caller) | Add validation for cross-service |
| MFA Service | `MfaService.kt` | 90% | EXTEND | 🟡 (5 callers) | Add recovery codes — backward compatible |
| Session Policy | `SessionPolicyService.kt` | 85% | EXTEND | 🟢 (2 callers) | Add concurrent enforcement |

### EXTRACT Details

> [CHANGED] No EXTRACT needed — key extraction (`AbstractTwoTierCache`) was already completed in previous iteration. All reuse is via direct REUSE or EXTEND of existing code.

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
| `AbstractTwoTierCache<K, V>` | Abstract class (extends) | `get()`, `put()`, `evict()` | L1+L2 cache orchestrator |
| `TreeEntity` | @MappedSuperclass (extends) | `parentId`, `name`, `code`, `sortOrder`, `treeLevel`, `treePath` | system-admin hierarchical data |
| `TreeBuilder` | Utility (static) | `buildTree()`, `detectCycle()`, `calculateTreePath()`, `validateMaxDepth()` | system-admin tree operations |
| `IdempotencyFilter` | OncePerRequestFilter (extends) | `doFilterInternal()` | Redis-backed deduplication |

### Config Keys

| Key | Source | Ví dụ value | Nơi dùng |
|-----|--------|------------|---------|
| `spring.data.redis.*` | application.yml | `localhost:6379` | RedisConfig |
| `spring.kafka.bootstrap-servers` | application.yml | `localhost:9092` | KafkaConfig (new) |
| `auth.jwt.private-key-path` | SecurityProperties | RSA private key | JwtService |
| `auth.mfa.otp-ttl-seconds` | application.yml | `300` | OtpService |
| `auth.mfa.recovery-code-count` | application.yml | `10` | MfaService (new) |
| `auth.session.max-concurrent` | application.yml | `5` | SessionPolicyService |
| `auth.service-token.secret` | application.yml | `shared-secret` | ServiceTokenService |
| `auth.http-client.connect-timeout` | application.yml | `5000` | HttpClientConfig |
| `auth.http-client.read-timeout` | application.yml | `10000` | HttpClientConfig |
| `auth.circuit-breaker.failure-rate` | application.yml | `50` | Resilience4j config |

### Error Codes Thrown

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| `AUTH_001` | Invalid credentials | AuthService.authenticate() |
| `AUTH_002` | Account locked | AuthService.authenticate() |
| `AUTH_003` | Token expired | JwtService.validateToken() |
| `AUTH_011–013` | MFA failures | MfaService |
| `AUTH_019–020` | Rate limits | MfaRateLimitService, LoginRateLimitService |
| `AUTH_050` | Invalid service token | ServiceTokenService.validateServiceToken() (new) |
| `AUTH_051` | Insufficient scope | ServiceAuthFilter (new) |
| `AUTH_052` | Service not registered | ServiceTokenService.validateServiceToken() (new) |
| `AUTH_053` | Audit export failed | AuditLogService (new) |
| `ACCT_001–008` | Account errors | account-service (existing) |
| `SYS_001–018` | System admin errors | system-admin-service (existing) |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| MFA recovery code request/response | `MfaDtos.kt` | 50% | NEW DTOs in same file |
| Session list response | N/A | 0% | NEW |
| Audit log response | N/A | 0% | NEW |
| Service token request/response | N/A | 0% | NEW |
| Domain branding request | N/A | 0% | NEW |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| EventPublisher.publish() | `fun publish(event: DomainEvent)` | grep_search | ✅ |
| AuditLogService.logEvent() | `fun logEvent(userId, action, entityType, entityId, details)` | view_file | ✅ |
| MfaService.isMfaEnabled() | `fun isMfaEnabled(userId: Long)` | grep_search | ✅ |
| SessionPromotionService.promote() | `fun promoteSession(...)` | grep_search | ✅ |
| ServiceTokenService.generateToken() | `fun generateServiceToken(...)` | grep_search | ✅ |
| AbstractTwoTierCache.get() | `abstract fun get(key: K): V?` | grep_search | ✅ |
| IdempotencyFilter | `class IdempotencyFilter(...)` | grep_search | ✅ |
| TreeBuilder.buildTree() | confirmed in system-admin-service | pre_openspec.md | ✅ |
