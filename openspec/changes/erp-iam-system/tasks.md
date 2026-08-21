<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: erp-iam-system

_Generated: 2025-07-15_
_Profile: Non-Financial | N/A | EXTEND_
_Direction: Capability-Priority Enhancement with Event-Driven Redis Backbone_

---

## Changes

- **auth-service — MFA Recovery Codes [MODIFY]**: `MfaService.kt`, `MfaController.kt`, `MfaDtos.kt` — add recovery codes management (generate 10 single-use, regenerate endpoint), enhance progressive flow (partial JWT → full JWT), trusted device MFA skip integration with account-service (FR-001, FR-002). New files: `MfaRecoveryCodeEntity.kt`, `MfaRecoveryCodeRepository.kt`, `V10__mfa_recovery_codes.sql`.
- **auth-service — Inter-service Auth [MODIFY]**: `ServiceTokenService.kt`, `SecurityConfig.kt` — enhance for cross-service JWT validation. New files: `ServiceAuthFilter.kt`, `InternalApiController.kt`. Error codes AUTH_050–AUTH_052 added to `AuthErrorCode.kt` (FR-021).
- **auth-service — Kafka Migration [MODIFY]**: `build.gradle.kts` — `compileOnly` → `implementation` for spring-kafka. `EventPublisher.kt` — add new event types. New files: `KafkaEventPublisher.kt`, `KafkaConfig.kt` (FR-020).
- **auth-service — Timeout & Circuit Breaker [MODIFY]**: `HttpClientConfig.kt` — configurable timeout + Resilience4j circuit breaker. `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt` — @CircuitBreaker annotations (FR-019).
- **auth-service — SSO Enhancement [MODIFY]**: `SsoAdapter.kt` — publish `SsoProvisionedEvent` via Kafka for account-service profile creation (FR-003).
- **auth-service — Session Management [MODIFY]**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt` — concurrent session enforcement, terminate oldest on exceed, session list endpoint (FR-008).
- **auth-service — Audit Persistence [MODIFY]**: `AuditLogService.kt` — implement actual DB persistence (replace TODO), add sensitive data masking. New files: `AuditLogEntity.kt`, `AuditLogRepository.kt`, `V11__audit_logs.sql` (FR-015).
- **auth-service — Account Lifecycle GDPR [MODIFY]**: `AccountLifecycleService.kt` — publish `AccountDeactivatedEvent`, enhanced data export (FR-009).
- **auth-service — Domain Config [MODIFY]**: `DomainLookupService.kt`, `RbacEntities.kt` (DomainEntity) — branding fields (logo_url, primary_color, login_page_config JSONB). `V12__domain_branding.sql` (FR-016).
- **account-service — Profile Enhancement [MODIFY]**: `ProfileService.kt`, `ProfileController.kt`, `ProfileDtos.kt` — email/phone verification flow, avatar upload, completeness score. New files: `ProfileVerificationEntity.kt`, `V4__profile_enhancements.sql` (FR-005).
- **account-service — Device Enhancement [MODIFY]**: `DeviceService.kt`, `DeviceController.kt`, `DeviceDtos.kt` — trusted device management, 30-day TTL, max 5 devices. New files: `InternalDeviceController.kt`, `V5__trusted_device.sql` (FR-007).
- **account-service — Preference Enhancement [MODIFY]**: `PreferenceService.kt`, `PreferenceController.kt` — category-based query, bulk merge-update (FR-006).
- **account-service — Idempotency [REUSE]**: Replicate `IdempotencyFilter.kt` pattern from auth-service (FR-017).
- **system-admin-service — Menu Permission [MODIFY]**: `MenuPermissionService.kt` — button-level permission, role-menu assignment CRUD, user override, Kafka-driven cache invalidation. New files: `PermissionChangedMenuConsumer.kt`, `V5__role_menu_permissions.sql` (FR-010).
- **system-admin-service — Organization [MODIFY]**: `OrganizationService.kt`, `PositionService.kt`, `DepartmentController.kt` — user transfer, org chart recursive CTE, department head assignment (FR-011).
- **system-admin-service — API Partner [MODIFY]**: `ApiPartnerService.kt`, `ApiUsageController.kt` — Bucket4j rate limiting (Redis ProxyManager), API key lifecycle (generate/rotate/revoke, show-once pattern `ntt_pk_`/`ntt_sk_`), IP whitelist, usage dashboard (FR-012).
- **system-admin-service — Workflow [MODIFY]**: `WorkflowEngine.kt`, `WorkflowService.kt` — conditional routing (PBAC PolicyCondition DSL reuse), delegation support, explicit guard conditions per transition (FR-013).
- **system-admin-service — Config [MODIFY]**: `DomainConfigService.kt`, `FeatureFlagService.kt` — config versioning with history, type validation, percentage rollout. New files: `V7__config_history.sql` (FR-014).
- **system-admin-service — Audit [MODIFY]**: `AuditAspect.kt`, `AuditService.kt`, `AuditController.kt` — immutability DB constraints, sensitive data masking, CSV/JSON export (FR-015).
- **system-admin-service — Idempotency [REUSE]**: Replicate `IdempotencyFilter.kt` pattern from auth-service (FR-017).

**Total**: ~12 new files, ~35 modified files across 3 services. 50 tasks in 3 capability-priority phases. 57+ API endpoints. ~6-9 weeks effort.

---

## Phase 1: Security-Critical Enhancements

### 1.1 MFA Recovery Codes (FR-001)

- [ ] **T01: Create MfaRecoveryCodeEntity** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MfaRecoveryCodeEntity.kt`
  - Base: `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-001 — Recovery codes single-use, 10 codes generated on MFA setup
  - Fields: `userId: Long`, `codeHash: String` (SHA-256), `used: Boolean`, `usedAt: Instant?`
  - Dependencies: `SnowflakePersistentAuditableEntity`

- [ ] **T02: Create MfaRecoveryCodeRepository** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/MfaRecoveryCodeRepository.kt`
  - Pattern: `JpaRepository<MfaRecoveryCodeEntity, Long>`
  - Methods: `findByUserIdAndUsedFalse()`, `deleteByUserId()`

- [ ] **T03: Flyway migration for mfa_recovery_codes table** `[NEW]`
  - File: `src/main/resources/db/migration/V10__mfa_recovery_codes.sql`
  - Table: `mfa_recovery_codes` (id, user_id, code_hash, used, used_at, created_at, updated_at)
  - Index: `idx_mfa_recovery_codes_user_id` on `user_id`

- [ ] **T04: Enhance MfaService with recovery codes** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - FR: FR-001 — Generate 10 single-use codes on MFA setup, regenerate endpoint
  - Methods: `generateRecoveryCodes(userId): List<String>`, `verifyRecoveryCode(userId, code): Boolean`, `regenerateRecoveryCodes(userId): List<String>`
  - Pattern: Generate 16-char alphanumeric → SHA-256 hash → store hash, return plain (show-once)
  - Dependencies: `MfaRecoveryCodeRepository`, `AuditLogService`

- [ ] **T05: Add recovery code endpoints to MfaController** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - FR: FR-001 — Recovery codes management
  - Endpoints: `POST /api/auth/mfa/recovery-codes`, `POST /api/auth/mfa/recovery-codes/verify`, `POST /api/auth/mfa/recovery-codes/regenerate`
  - Dependencies: `MfaService`

- [ ] **T06: Add recovery code DTOs** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - DTOs: `RecoveryCodeVerifyRequest`, `RecoveryCodesResponse`

### 1.2 Inter-service Auth (FR-021)

- [ ] **T07: Enhance ServiceTokenService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ServiceTokenService.kt`
  - FR: FR-021 — Service-level JWT for inter-service communication
  - Methods: `validateServiceToken(token): ServiceClaims`, `isServiceAuthorized(claims, requiredScope): Boolean`
  - Error: `AUTH_050` (invalid service token), `AUTH_051` (insufficient scope), `AUTH_052` (service not registered)
  - Dependencies: `JwtService`, `SecurityProperties`

- [ ] **T08: Create ServiceAuthFilter** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/ServiceAuthFilter.kt`
  - FR: FR-021 — Internal endpoint security
  - Base: `OncePerRequestFilter`
  - Pattern: Match `/api/internal/**` → extract Bearer → validate via ServiceTokenService → set SecurityContext with SERVICE authority
  - Dependencies: `ServiceTokenService`

- [ ] **T09: Add error codes AUTH_050-052** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - FR: FR-021 — Inter-service auth error codes
  - Codes: `AUTH_050` (invalid_service_token, 401), `AUTH_051` (insufficient_scope, 403), `AUTH_052` (service_not_registered, 403)

- [ ] **T10: Update SecurityConfig for internal endpoints** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt`
  - FR: FR-021 — Add filter chain for /api/internal/**
  - Pattern: `http.securityMatcher("/api/internal/**").addFilterBefore(serviceAuthFilter, ...)`
  - Dependencies: `ServiceAuthFilter`

### 1.3 Bucket4j Rate Limiting (FR-012)

- [ ] **T11: Enhance ApiPartnerService with Bucket4j** `[MODIFY]`
  - File: `system-admin-service — ApiPartnerService.kt`
  - FR: FR-012 — Rate limiting config CRUD + push to Redis
  - Methods: `configureRateLimit(partnerId, config)`, `pushRateLimitToRedis(keyHash, config)`
  - Pattern: Persist config → push to Redis key `rate_limit:bucket4j:{keyHash}` → Gateway reads
  - Dependencies: `StringRedisTemplate`, `ApiPartnerRepository`

- [ ] **T12: Add API key lifecycle endpoints** `[MODIFY]`
  - File: `system-admin-service — ApiPartnerService.kt + ApiUsageController.kt`
  - FR: FR-012 — Generate (show-once, ntt_pk_/ntt_sk_ prefix), rotate, revoke
  - Pattern: Generate random → SHA-256 hash → store hash → return plain once
  - Methods: `generateApiKey(partnerId)`, `rotateApiKey(partnerId, keyId)`, `revokeApiKey(keyId)`

- [ ] **T13: Add IP whitelist management** `[MODIFY]`
  - File: `system-admin-service — ApiPartnerService.kt`
  - FR: FR-012 — IP whitelist with CIDR notation support
  - Methods: `updateIpWhitelist(partnerId, ips: List<String>)`, `isIpAllowed(partnerId, ip): Boolean`

### 1.4 SSO Enhancement (FR-003)

- [ ] **T14: Enhance SsoAdapter with Kafka event** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`
  - FR: FR-003 — Publish SsoProvisionedEvent for account-service profile creation
  - Pattern: After successful SSO auto-provision → `eventPublisher.publishSsoProvisioned(event)`
  - Dependencies: `EventPublisher`

### 1.5 Timeout & Circuit Breaker (FR-019)

- [ ] **T15: Enhance HttpClientConfig with timeout + circuit breaker** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`
  - FR: FR-019 — Configurable timeout + Resilience4j circuit breaker
  - Pattern: `connectTimeout: 5s`, `readTimeout: 10s`, circuit breaker (50% failure → open 30s)
  - Dependencies: `resilience4j-spring-boot3` (add to build.gradle.kts)

- [ ] **T16: Add @CircuitBreaker to external gateways** `[MODIFY]`
  - Files: `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt`
  - FR: FR-019 — Circuit breaker on external API calls
  - Pattern: `@CircuitBreaker(name = "ssoProvider", fallbackMethod = "ssoFallback")`

---

## Phase 2: Admin Feature Enhancements

### 2.1 Menu Permission (FR-010)

- [ ] **T17: Flyway migration for role_menu_permissions + user_menu_overrides** `[NEW]`
  - File: `system-admin-service — V5__role_menu_permissions.sql`
  - Tables: `role_menu_permissions` (role_id, menu_item_id), `user_menu_overrides` (user_id, menu_item_id, action GRANT/DENY)

- [ ] **T18: Enhance MenuPermissionService with button-level + user override** `[MODIFY]`
  - File: `system-admin-service — MenuPermissionService.kt`
  - FR: FR-010 — Button-level permission (BUTTON type with permission_code), user override > role permission
  - Methods: `assignRolePermissions(roleId, menuIds)`, `setUserOverride(userId, menuId, action)`, `getUserMenuTree(userId, domainId)`
  - Pattern: User override > role permission → filter tree → cache Redis 5min
  - Dependencies: `TreeBuilder`, `StringRedisTemplate`

- [ ] **T19: Add Kafka consumer for menu cache invalidation** `[NEW]`
  - File: `system-admin-service — PermissionChangedMenuConsumer.kt`
  - FR: FR-010 — Cache invalidation on permission change
  - Pattern: `@KafkaListener(topics = "iam.permission.changed")` → delete Redis key `user:*:menu:*`

### 2.2 Organization (FR-011)

- [ ] **T20: Enhance OrganizationService with user transfer + org chart** `[MODIFY]`
  - File: `system-admin-service — OrganizationService.kt`
  - FR: FR-011 — User transfer between departments, org chart recursive CTE
  - Methods: `transferUser(userId, fromDeptId, toDeptId, toPositionId)`, `getOrgChart(domainId)`
  - Dependencies: `AuditService`, `EventPublisher` (Kafka)

- [ ] **T21: Add department head assignment** `[MODIFY]`
  - File: `system-admin-service — PositionService.kt + DepartmentEntity.kt`
  - FR: FR-011 — Set headUserId on department
  - Method: `assignDepartmentHead(deptId, userId)`

### 2.3 Workflow Enhancement (FR-013)

- [ ] **T22: Enhance WorkflowEngine with conditional routing** `[MODIFY]`
  - File: `system-admin-service — WorkflowEngine.kt`
  - FR: FR-013 — Conditional routing using PBAC PolicyCondition JSONB DSL
  - Pattern: Evaluate step conditions → route to appropriate next step
  - Dependencies: Reuse PBAC condition evaluation pattern

- [ ] **T23: Add delegation support to WorkflowService** `[MODIFY]`
  - File: `system-admin-service — WorkflowService.kt`
  - FR: FR-013 — Delegate approval step to another user
  - Method: `delegateStep(instanceId, stepId, delegateTo, reason)`
  - Error: `SYS_014` (delegation failed)

- [ ] **T24: Enhance WorkflowEngine guard conditions** `[MODIFY]`
  - File: `system-admin-service — WorkflowEngine.kt`
  - FR: FR-013 — Explicit guard per transition: canResubmit, canCancel, isEscalated
  - Pattern: `VALID_TRANSITIONS` map → add guard condition functions

### 2.4 Profile Enhancement (FR-005)

- [ ] **T25: Enhance ProfileService with verification + avatar** `[MODIFY]`
  - File: `account-service — ProfileService.kt`
  - FR: FR-005 — Email/phone verification flow, avatar upload, completeness score
  - Methods: `uploadAvatar(userId, file)`, `initiateEmailVerification(userId)`, `getProfileCompleteness(userId)`
  - Dependencies: REST call to auth-service `/api/internal/otp/send`

- [ ] **T26: Flyway migration for profile enhancements** `[NEW]`
  - File: `account-service — V4__profile_enhancements.sql`
  - Changes: Add `avatar_url`, `completeness_score` to `user_profiles`. Create `profile_verifications` table.

### 2.5 Device Enhancement (FR-007)

- [ ] **T27: Enhance DeviceService with trusted device management** `[MODIFY]`
  - File: `account-service — DeviceService.kt`
  - FR: FR-007 — Trust/untrust, TTL 30 days, max 5 devices, internal API for MFA skip
  - Methods: `trustDevice(userId, fingerprint)`, `untrustDevice(userId, deviceId)`, `isDeviceTrusted(userId, fingerprint)`
  - Error: `ACCT_006` (max devices), `ACCT_007` (already trusted)

- [ ] **T28: Flyway migration for trusted device fields** `[NEW]`
  - File: `account-service — V5__trusted_device.sql`
  - Changes: Add `trusted_at`, `trusted_expiry` to `user_devices`

- [ ] **T29: Add internal API for trusted device check** `[NEW]`
  - File: `account-service — InternalDeviceController.kt`
  - Endpoint: `GET /api/internal/devices/{userId}/trusted?fingerprint={hash}`
  - Auth: Service JWT (via ServiceAuthFilter)

### 2.6 Session Enhancement (FR-008)

- [ ] **T30: Enhance LoginSessionService with concurrent enforcement** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
  - FR: FR-008 — Max concurrent sessions, terminate oldest on exceed
  - Methods: `enforceConcurrentLimit(userId)`, `getActiveSessions(userId)`
  - Dependencies: `SessionPolicyService`, `LoginSessionRepository`

- [ ] **T31: Add session list endpoint** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - Endpoints: `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{sessionId}`

---

## Phase 3: Integration & Polish

### 3.1 Kafka Event Migration (FR-020)

- [ ] **T32: Create KafkaEventPublisher** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - FR: FR-020 — Implements EventPublisher port with Kafka
  - Pattern: `KafkaTemplate<String, DomainEvent>`, retry max 3, exponential backoff (1s, 2s, 4s)
  - Dependencies: `KafkaTemplate`, `ObjectMapper`

- [ ] **T33: Create KafkaConfig** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
  - FR: FR-020 — DLQ with .DLT topic suffix, JSON serializer config
  - Dependencies: `spring-kafka` (change `compileOnly` → `implementation` in build.gradle.kts)

- [ ] **T34: Update build.gradle.kts for Kafka** `[MODIFY]`
  - File: `build.gradle.kts`
  - FR: FR-020 — `compileOnly("org.springframework.kafka:spring-kafka")` → `implementation(...)`

- [ ] **T35: Enhance EventPublisher port with new events** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - FR: FR-020 — Add `publishAccountDeactivated()`, `publishAccountDeleted()`, `publishOrgStructureChanged()`

### 3.2 Audit Trail Enhancement (FR-015)

- [ ] **T36: Implement AuditLogService DB persistence** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt`
  - FR: FR-015 — Replace TODO with actual DB insert, add sensitive data masking
  - Pattern: Build AuditLogEntity → maskSensitiveFields(details) → repository.save()
  - Dependencies: `AuditLogRepository` (new)

- [ ] **T37: Create AuditLogEntity + Repository (auth-service)** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogEntity.kt`
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogRepository.kt`
  - Base: `SnowflakePersistentAuditableEntity`
  - Fields: userId, action, entityType, entityId, details (JSONB), ipAddress, userAgent

- [ ] **T38: Flyway migration for audit_logs table + immutability** `[NEW]`
  - File: `src/main/resources/db/migration/V11__audit_logs.sql`
  - Table: `audit_logs` with JSONB details
  - Constraint: CREATE RULE to prevent UPDATE/DELETE on audit_logs

- [ ] **T39: Enhance system-admin AuditAspect + export** `[MODIFY]`
  - File: `system-admin-service — AuditAspect.kt, AuditService.kt, AuditController.kt`
  - FR: FR-015 — Immutability constraints, sensitive masking, CSV/JSON export endpoint

### 3.3 Config Enhancement (FR-014)

- [ ] **T40: Enhance DomainConfigService with versioning** `[MODIFY]`
  - File: `system-admin-service — DomainConfigService.kt`
  - FR: FR-014 — Config versioning with history tracking, type validation
  - Methods: `updateConfig(key, value, type)` → increment version, create config_history record

- [ ] **T41: Flyway migration for config_history** `[NEW]`
  - File: `system-admin-service — V7__config_history.sql`
  - Table: `config_history` (config_key, domain_id, old_value, new_value, version, changed_by, changed_at)

- [ ] **T42: Enhance FeatureFlagService** `[MODIFY]`
  - File: `system-admin-service — FeatureFlagService.kt`
  - FR: FR-014 — Percentage rollout, segment targeting

### 3.4 Domain Config Enhancement (FR-016)

- [ ] **T43: Add branding fields to DomainEntity** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/RbacEntities.kt`
  - FR: FR-016 — Add logoUrl, primaryColor, loginPageConfig (JSONB)

- [ ] **T44: Flyway migration for domain branding** `[NEW]`
  - File: `src/main/resources/db/migration/V12__domain_branding.sql`
  - Changes: ALTER domain table ADD logo_url, primary_color, login_page_config

- [ ] **T45: Enhance DomainLookupService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/DomainLookupService.kt`
  - FR: FR-016 — Return branding config in domain lookup

### 3.5 Account Lifecycle GDPR (FR-009)

- [ ] **T46: Enhance AccountLifecycleService with cross-service events** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt`
  - FR: FR-009 — Publish AccountDeactivatedEvent, enhanced data export
  - Dependencies: `EventPublisher`, `AuditLogService`

### 3.6 Idempotency Replication (FR-017)

- [ ] **T47: Replicate IdempotencyFilter to account-service** `[REUSE]`
  - File: `account-service — IdempotencyFilter.kt`
  - Source: `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - FR: FR-017 — Copy pattern, configure for account-service POST endpoints

- [ ] **T48: Replicate IdempotencyFilter to system-admin-service** `[REUSE]`
  - File: `system-admin-service — IdempotencyFilter.kt`
  - Source: `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - FR: FR-017 — Copy pattern, configure for system-admin-service POST endpoints

### 3.7 Preference Enhancement (FR-006)

- [ ] **T49: Enhance PreferenceService** `[MODIFY]`
  - File: `account-service — PreferenceService.kt + PreferenceController.kt`
  - FR: FR-006 — Category-based query, bulk merge-update
  - Endpoint: `GET /api/account/preferences/{category}`, `PATCH /api/account/preferences`

### 3.8 Retry Mechanism (FR-020 supplement)

- [ ] **T50: Add retry configuration to KafkaEventPublisher** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - FR: FR-020 — Max 3 retries, exponential backoff (1s, 2s, 4s), DLQ on failure
  - Dependencies: Spring Retry (`@Retryable`)

---

## FR Traceability

| FR | Tasks | Coverage |
|----|-------|----------|
| FR-001 | T01-T06 | ✅ Recovery codes entity, repo, migration, service, controller, DTOs |
| FR-002 | T04 (recovery verify → session promotion) | ✅ Progressive flow via recovery codes |
| FR-003 | T14 | ✅ SSO auto-provision event |
| FR-004 | — | ✅ REUSE (no changes needed) |
| FR-005 | T25-T26 | ✅ Profile verification, avatar, completeness |
| FR-006 | T49 | ✅ Preference category query, merge-update |
| FR-007 | T27-T29 | ✅ Device trust, TTL, max limit, internal API |
| FR-008 | T30-T31 | ✅ Concurrent session enforcement, list endpoint |
| FR-009 | T46 | ✅ GDPR lifecycle events |
| FR-010 | T17-T19 | ✅ Button-level permission, user override, cache invalidation |
| FR-011 | T20-T21 | ✅ User transfer, org chart, department head |
| FR-012 | T11-T13 | ✅ Bucket4j rate limiting, key lifecycle, IP whitelist |
| FR-013 | T22-T24 | ✅ Conditional routing, delegation, guard conditions |
| FR-014 | T40-T42 | ✅ Config versioning, feature flags enhancement |
| FR-015 | T36-T39 | ✅ Audit persistence, immutability, export |
| FR-016 | T43-T45 | ✅ Domain branding, config enhancement |
| FR-017 | T47-T48 | ✅ Idempotency replication |
| FR-018 | — | ✅ REUSE (HttpLoggingFilter from base-core) |
| FR-019 | T15-T16 | ✅ Timeout + circuit breaker |
| FR-020 | T32-T35, T50 | ✅ Kafka migration, retry, DLQ |
| FR-021 | T07-T10 | ✅ Service JWT, auth filter, security config |

**Coverage: 21/21 FRs → 50 tasks**

---

## Open Items

- ⚠️ OPEN QUESTION: OQ-001 — Verify Kafka dependency scope in `build.gradle.kts` before T34
- ⚠️ OPEN QUESTION: OQ-002 — DPoP support → future consideration (not in current tasks)
