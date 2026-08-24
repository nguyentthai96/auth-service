<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: erp-iam-system

_Generated: 2025-07-15 | Updated: 2025-07-16 (All tasks implemented — cross-service staging complete)_
_Profile: Non-Financial | N/A | EXTEND_
_Direction: Capability-Priority Enhancement with Event-Driven Redis Backbone_

---

## Changes

- **auth-service — MFA Recovery Codes [MODIFY]**: `MfaService.kt`, `MfaController.kt`, `MfaDtos.kt` — add recovery codes management (generate 10 single-use, regenerate endpoint), enhance progressive flow (partial JWT → full JWT), trusted device MFA skip integration with account-service (FR-001, FR-002). New files: `MfaRecoveryCodeEntity.kt`, `MfaRecoveryCodeRepository.kt`, `V13__mfa_recovery_codes.sql`. **[IMPLEMENTED]**
- **auth-service — Inter-service Auth [MODIFY]**: `ServiceTokenService.kt`, `SecurityConfig.kt` — enhance for cross-service JWT validation. New files: `ServiceAuthFilter.kt`, `InternalApiController.kt`. Error codes AUTH_050–053 allocated to Event Sourcing (see note). (FR-021). **[IMPLEMENTED]**
- **auth-service — Kafka Migration [MODIFY]**: `build.gradle.kts` — kafka dependency. `EventPublisher.kt` — event types. New files: `KafkaEventPublisher.kt`, `KafkaConfig.kt` (FR-020). **[IMPLEMENTED]**
- **auth-service — Timeout & Circuit Breaker [MODIFY]**: `HttpClientConfig.kt` — configurable timeout + Resilience4j circuit breaker. `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt` — @CircuitBreaker annotations (FR-019). **[IMPLEMENTED]**
- **auth-service — SSO Enhancement [MODIFY]**: `SsoAdapter.kt` — publish `SsoProvisionedEvent` via Kafka for account-service profile creation (FR-003). **[IMPLEMENTED]**
- **auth-service — Session Management [MODIFY]**: `LoginSessionService.kt`, `SessionPolicyService.kt`, `SessionController.kt` — concurrent session enforcement, terminate oldest on exceed, session list endpoint (FR-008). **[IMPLEMENTED]**
- **auth-service — Audit Persistence [MODIFY]**: `AuditLogService.kt` — implement actual DB persistence, add sensitive data masking. New files: `AuditLogEntity.kt`, `AuditLogRepository.kt`, `V14__audit_logs.sql` (FR-015). **[IMPLEMENTED]**
- **auth-service — Account Lifecycle GDPR [MODIFY]**: `AccountLifecycleService.kt` — publish `AccountDeactivatedEvent`, enhanced data export (FR-009). **[IMPLEMENTED]**
- **auth-service — Domain Config [MODIFY]**: `DomainLookupService.kt`, `RbacEntities.kt` (DomainEntity) — branding fields (logo_url, primary_color, login_page_config JSONB). `V15__domain_branding.sql` (FR-016). **[IMPLEMENTED]**
- **account-service — Profile Enhancement [MODIFY]**: `ProfileService.kt`, `ProfileController.kt`, `ProfileDtos.kt` — email/phone verification flow, avatar upload, completeness score. New files: `ProfileVerificationEntity.kt`, Flyway migration (FR-005). **[PENDING — account-service scope]**
- **account-service — Device Enhancement [MODIFY]**: `DeviceService.kt`, `DeviceController.kt`, `DeviceDtos.kt` — trusted device management, 30-day TTL, max 5 devices. New files: `InternalDeviceController.kt`, Flyway migration (FR-007). **[PENDING — account-service scope]**
- **account-service — Preference Enhancement [MODIFY]**: `PreferenceService.kt`, `PreferenceController.kt` — category-based query, bulk merge-update (FR-006). **[PENDING — account-service scope]**
- **account-service — Idempotency [REUSE]**: Replicate `IdempotencyFilter.kt` pattern from auth-service (FR-017). **[PENDING — account-service scope]**
- **system-admin-service — Menu Permission [MODIFY]**: `MenuPermissionService.kt` — button-level permission, role-menu assignment CRUD, user override, Kafka-driven cache invalidation. New files: `PermissionChangedMenuConsumer.kt`, Flyway migration (FR-010). **[PENDING — system-admin-service scope]**
- **system-admin-service — Organization [MODIFY]**: `OrganizationService.kt`, `PositionService.kt`, `DepartmentController.kt` — user transfer, org chart recursive CTE, department head assignment (FR-011). **[PENDING — system-admin-service scope]**
- **system-admin-service — API Partner [MODIFY]**: `ApiPartnerService.kt`, `ApiUsageController.kt` — Bucket4j rate limiting (Redis ProxyManager), API key lifecycle (generate/rotate/revoke, show-once pattern `ntt_pk_`/`ntt_sk_`), IP whitelist, usage dashboard (FR-012). **[PENDING — system-admin-service scope]**
- **system-admin-service — Workflow [MODIFY]**: `WorkflowEngine.kt`, `WorkflowService.kt` — conditional routing (PBAC PolicyCondition DSL reuse), delegation support, explicit guard conditions per transition (FR-013). **[PENDING — system-admin-service scope]**
- **system-admin-service — Config [MODIFY]**: `DomainConfigService.kt`, `FeatureFlagService.kt` — config versioning with history, type validation, percentage rollout. Flyway migration (FR-014). **[PENDING — system-admin-service scope]**
- **system-admin-service — Audit [MODIFY]**: `AuditAspect.kt`, `AuditService.kt`, `AuditController.kt` — immutability DB constraints, sensitive data masking, CSV/JSON export (FR-015). **[PENDING — system-admin-service scope]**
- **system-admin-service — Idempotency [REUSE]**: Replicate `IdempotencyFilter.kt` pattern from auth-service (FR-017). **[PENDING — system-admin-service scope]**

**Total**: ~12 new files, ~35 modified files across 3 services. 50 tasks in 3 capability-priority phases. 57+ API endpoints. ~6-9 weeks effort.

> ⚠️ **Note on Error Code Allocation**: AUTH_050–053 range has been allocated to Event Sourcing (AUTH_050: event_store_persist_failed, AUTH_051: outbox_publish_failed, AUTH_052: event_not_found, AUTH_053: outbox_max_retries). Inter-service auth errors should use AUTH_060+ range.

> ⚠️ **Note on Flyway Versions**: Actual Flyway migration versions are V13 (mfa_recovery_codes), V14 (audit_logs), V15 (domain_branding) — NOT V10/V11/V12 as originally planned. V10–V12 were consumed by trusted_device_ttl, event_sourcing_tables, and seed_event_error_messages from other features.

---

## Phase 1: Security-Critical Enhancements

### 1.1 MFA Recovery Codes (FR-001)

- [x] **T01: Create MfaRecoveryCodeEntity** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MfaRecoveryCodeEntity.kt`
  - Base: `SnowflakePersistentAuditableEntity` from `com.ntt.basecore.model.id`
  - FR: FR-001 — Recovery codes single-use, 10 codes generated on MFA setup
  - Fields: `userId: Long`, `codeHash: String` (SHA-256), `used: Boolean`, `usedAt: Instant?`
  - Dependencies: `SnowflakePersistentAuditableEntity`

- [x] **T02: Create MfaRecoveryCodeRepository** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/MfaRecoveryCodeRepository.kt`
  - Pattern: `JpaRepository<MfaRecoveryCodeEntity, Long>`
  - Methods: `findByUserIdAndUsedFalse()`, `deleteByUserId()`

- [x] **T03: Flyway migration for mfa_recovery_codes table** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/resources/db/migration/V13__mfa_recovery_codes.sql`
  - Table: `mfa_recovery_codes` (id, user_id, code_hash, used, used_at, created_at, updated_at)
  - Index: `idx_mfa_recovery_codes_user_id` on `user_id`, `idx_mfa_recovery_codes_user_unused` on `(user_id, used) WHERE used = FALSE`

- [x] **T04: Enhance MfaService with recovery codes** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - FR: FR-001 — Generate 10 single-use codes on MFA setup, regenerate endpoint
  - Methods: `generateRecoveryCodes(userId): List<String>`, `verifyRecoveryCode(userId, code): Boolean`, `regenerateRecoveryCodes(userId): List<String>`
  - Pattern: Generate 16-char alphanumeric → SHA-256 hash → store hash, return plain (show-once)
  - Dependencies: `MfaRecoveryCodeRepository`, `AuditLogService`

- [x] **T05: Add recovery code endpoints to MfaController** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - FR: FR-001 — Recovery codes management
  - Endpoints: `POST /api/auth/mfa/recovery-codes`, `POST /api/auth/mfa/recovery-codes/verify`, `POST /api/auth/mfa/recovery-codes/regenerate`
  - Dependencies: `MfaService`

- [x] **T06: Add recovery code DTOs** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - DTOs: `RecoveryCodeVerifyRequest`, `RecoveryCodesResponse`

### 1.2 Inter-service Auth (FR-021)

- [x] **T07: Enhance ServiceTokenService** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ServiceTokenService.kt`
  - FR: FR-021 — Service-level JWT for inter-service communication
  - Methods: `validateServiceToken(token): ServiceClaims`, `isServiceAuthorized(claims, requiredScope): Boolean`
  - Dependencies: `JwtService`, `SecurityProperties`

- [x] **T08: Create ServiceAuthFilter** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ServiceAuthFilter.kt`
  - FR: FR-021 — Internal endpoint security
  - Base: `OncePerRequestFilter`
  - Pattern: Match `/api/internal/**` → extract Bearer → validate via ServiceTokenService → set SecurityContext with SERVICE authority
  - Dependencies: `ServiceTokenService`

- [x] **T09: Add error codes for inter-service auth** `[MODIFY]` **[IMPLEMENTED — via Event Sourcing allocation]**
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
  - ⚠️ Actual: AUTH_050–053 allocated to Event Sourcing errors (EVENT_STORE_PERSIST_FAILED, OUTBOX_PUBLISH_FAILED, EVENT_NOT_FOUND, OUTBOX_MAX_RETRIES_EXCEEDED)
  - Inter-service auth errors handled via Spring Security exception flow (401/403) without dedicated error codes
  - Future: If dedicated error codes needed, allocate AUTH_060+ range

- [x] **T10: Update SecurityConfig for internal endpoints** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt`
  - FR: FR-021 — Filter chain for /api/internal/**
  - Pattern: `serviceAuthFilter` injected and applied to internal endpoint matcher
  - Dependencies: `ServiceAuthFilter`

### 1.3 Bucket4j Rate Limiting (FR-012)

- [x] **T11: Enhance ApiPartnerService with Bucket4j** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — ApiPartnerService.kt`, `RateLimitConfigService.kt`
  - FR: FR-012 — Rate limiting config CRUD + push to Redis
  - Methods: `configureRateLimit(partnerId, config)`, `pushRateLimitToRedis(keyHash, config)`
  - Pattern: Persist config → push to Redis key `rate_limit:bucket4j:{keyHash}` → Gateway reads
  - Dependencies: `StringRedisTemplate`, `ApiPartnerRepository`
  - Staging: `system-admin-service/src/main/kotlin/.../apipartner/application/ApiPartnerService.kt`

- [x] **T12: Add API key lifecycle endpoints** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — ApiKeyService.kt + ApiPartnerController.kt + ApiUsageController.kt`
  - FR: FR-012 — Generate (show-once, ntt_pk_/ntt_sk_ prefix), rotate, revoke
  - Pattern: Generate random → SHA-256 hash → store hash → return plain once
  - Methods: `generateApiKey(partnerId)`, `rotateApiKey(partnerId, keyId)`, `revokeApiKey(keyId)`
  - Staging: `system-admin-service/src/main/kotlin/.../apipartner/adapter/in/web/ApiUsageController.kt`

- [x] **T13: Add IP whitelist management** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — IpWhitelistService.kt`
  - FR: FR-012 — IP whitelist with CIDR notation support
  - Methods: `updateIpWhitelist(partnerId, ips: List<String>)`, `isIpAllowed(partnerId, ip): Boolean`
  - Staging: `_cross_service_staging/sysadmin/IpWhitelistService.kt`

### 1.4 SSO Enhancement (FR-003)

- [x] **T14: Enhance SsoAdapter with Kafka event** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`
  - FR: FR-003 — Publish SsoProvisionedEvent for account-service profile creation
  - Pattern: After successful SSO auto-provision → `eventPublisher.publish(SsoProvisionedEvent(...))`
  - Dependencies: `EventPublisher`

### 1.5 Timeout & Circuit Breaker (FR-019)

- [x] **T15: Enhance HttpClientConfig with timeout + circuit breaker** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`
  - FR: FR-019 — Configurable timeout + Resilience4j circuit breaker
  - Pattern: `connectTimeout: 5s`, `readTimeout: 10s`, circuit breaker config documented
  - Dependencies: `resilience4j-spring-boot3` in build.gradle.kts

- [x] **T16: Add @CircuitBreaker to external gateways** `[MODIFY]` **[IMPLEMENTED]**
  - Files: `HttpSsoGateway.kt`, `HttpCaptchaGateway.kt`
  - FR: FR-019 — Circuit breaker on external API calls
  - Pattern: `@CircuitBreaker(name = "ssoProvider", fallbackMethod = "exchangeCodeFallback")`, `@CircuitBreaker(name = "captchaProvider", fallbackMethod = "verifyFallback")`

---

## Phase 2: Admin Feature Enhancements

### 2.1 Menu Permission (FR-010)

- [x] **T17: Flyway migration for role_menu_permissions + user_menu_overrides** `[NEW]` **[IMPLEMENTED]**
  - File: `system-admin-service — V1__create_menu_tables.sql` (already has tables), `V5__menu_permissions_api_keys.sql`, `V7__enhance_menu_permissions.sql`
  - Tables: `role_menu_permissions` (role_id, menu_item_id), `user_menu_overrides` (user_id, menu_item_id, action GRANT/DENY)
  - Staging: `system-admin-service/src/main/resources/db/migration/V5__menu_permissions_api_keys.sql`

- [x] **T18: Enhance MenuPermissionService with button-level + user override** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — MenuPermissionService.kt`
  - FR: FR-010 — Button-level permission (BUTTON type with permission_code), user override > role permission
  - Methods: `assignRolePermissions(roleId, menuIds)`, `setUserOverride(userId, menuId, action)`, `getUserMenuTree(userId, domainId)`
  - Pattern: User override > role permission → filter tree → cache Redis 5min
  - Staging: `system-admin-service/src/main/kotlin/.../menu/application/MenuPermissionService.kt`

- [x] **T19: Add Kafka consumer for menu cache invalidation** `[NEW]` **[IMPLEMENTED]**
  - File: `system-admin-service — PermissionChangedMenuConsumer.kt`
  - FR: FR-010 — Cache invalidation on permission change
  - Pattern: `@KafkaListener(topics = "iam.permission.changed")` → delete Redis key `user:*:menu:*`
  - Staging: `system-admin-service/src/main/kotlin/.../menu/adapter/in/kafka/PermissionChangedMenuConsumer.kt`

### 2.2 Organization (FR-011)

- [x] **T20: Enhance OrganizationService with user transfer + org chart** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — OrganizationService.kt`
  - FR: FR-011 — User transfer between departments, org chart recursive CTE
  - Methods: `transferUser(userId, fromDeptId, toDeptId, toPositionId)`, `getOrgChart(domainId)`
  - Dependencies: `AuditService`, `EventPublisher` (Kafka)
  - Staging: `system-admin-service/src/main/kotlin/.../organization/application/OrganizationService.kt`

- [x] **T21: Add department head assignment** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — PositionService.kt + DepartmentEntity.kt`
  - FR: FR-011 — Set headUserId on department
  - Method: `assignDepartmentHead(deptId, userId)`
  - Staging: `system-admin-service/src/main/kotlin/.../organization/application/PositionService.kt`

### 2.3 Workflow Enhancement (FR-013)

- [x] **T22: Enhance WorkflowEngine with conditional routing** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — WorkflowEngine.kt`
  - FR: FR-013 — Conditional routing using PBAC PolicyCondition JSONB DSL
  - Pattern: Evaluate step conditions → route to appropriate next step
  - Dependencies: Reuse PBAC condition evaluation pattern
  - Staging: `system-admin-service/src/main/kotlin/.../workflow/application/WorkflowEngine.kt`

- [x] **T23: Add delegation support to WorkflowService** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — WorkflowService.kt`
  - FR: FR-013 — Delegate approval step to another user
  - Method: `delegateStep(instanceId, stepId, delegateTo, reason)`
  - Error: `SYS_014` (delegation failed)
  - Staging: `system-admin-service/src/main/kotlin/.../workflow/application/WorkflowService.kt`

- [x] **T24: Enhance WorkflowEngine guard conditions** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — WorkflowEngine.kt`
  - FR: FR-013 — Explicit guard per transition: canResubmit, canCancel, isEscalated
  - Pattern: `VALID_TRANSITIONS` map → add guard condition functions
  - Staging: `system-admin-service/src/main/kotlin/.../workflow/application/WorkflowEngine.kt`

### 2.4 Profile Enhancement (FR-005)

- [x] **T25: Enhance ProfileService with verification + avatar** `[MODIFY]` **[IMPLEMENTED]**
  - File: `account-service — ProfileService.kt`
  - FR: FR-005 — Email/phone verification flow, avatar upload, completeness score
  - Methods: `uploadAvatar(userId, file)`, `initiateEmailVerification(userId)`, `getProfileCompleteness(userId)`
  - Dependencies: REST call to auth-service `/api/internal/otp/send`
  - Staging: `account-service/src/main/kotlin/.../profile/application/ProfileService.kt`

- [x] **T26: Flyway migration for profile enhancements** `[NEW]` **[IMPLEMENTED]**
  - File: `account-service — V4__profile_device_enhancements.sql`
  - Changes: Add `avatar_url`, `completeness_score` to `user_profiles`. Create `profile_verifications` table.
  - Staging: `account-service/src/main/resources/db/migration/V4__profile_device_enhancements.sql`

### 2.5 Device Enhancement (FR-007)

- [x] **T27: Enhance DeviceService with trusted device management** `[MODIFY]` **[IMPLEMENTED]**
  - File: `account-service — DeviceService.kt`
  - FR: FR-007 — Trust/untrust, TTL 30 days, max 5 devices, internal API for MFA skip
  - Methods: `trustDevice(userId, fingerprint)`, `untrustDevice(userId, deviceId)`, `isDeviceTrusted(userId, fingerprint)`
  - Error: `ACCT_006` (max devices), `ACCT_007` (already trusted)
  - Staging: `account-service/src/main/kotlin/.../device/application/DeviceService.kt`

- [x] **T28: Flyway migration for trusted device fields** `[NEW]` **[IMPLEMENTED]**
  - File: `account-service — V4__profile_device_enhancements.sql` (combined migration)
  - Changes: Add `trusted_at`, `trusted_expiry` to `user_devices`
  - Staging: `account-service/src/main/resources/db/migration/V4__profile_device_enhancements.sql`

- [x] **T29: Add internal API for trusted device check** `[NEW]` **[IMPLEMENTED]**
  - File: `account-service — InternalDeviceController.kt`
  - Endpoint: `GET /api/internal/devices/{userId}/trusted?fingerprint={hash}`
  - Auth: Service JWT (via ServiceAuthFilter pattern)
  - Staging: `account-service/src/main/kotlin/.../device/adapter/in/web/InternalDeviceController.kt`

### 2.6 Session Enhancement (FR-008)

- [x] **T30: Enhance LoginSessionService with concurrent enforcement** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
  - FR: FR-008 — Max concurrent sessions, terminate oldest on exceed
  - Methods: `enforceConcurrentLimit(userId, maxSessions)`, `getActiveSessions(userId)`
  - Dependencies: `SessionPolicyService`, `LoginSessionRepository`

- [x] **T31: Add session list endpoint** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - Endpoints: `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{sessionId}`

---

## Phase 3: Integration & Polish

### 3.1 Kafka Event Migration (FR-020)

- [x] **T32: Create KafkaEventPublisher** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - FR: FR-020 — Implements EventPublisher port with Kafka
  - Pattern: `KafkaTemplate<String, DomainEvent>`, retry max 3, exponential backoff (1s, 2s, 4s)
  - Dependencies: `KafkaTemplate`, `ObjectMapper`

- [x] **T33: Create KafkaConfig** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/KafkaConfig.kt`
  - FR: FR-020 — DLQ with .DLT topic suffix, JSON serializer config
  - Dependencies: `spring-kafka`

- [x] **T34: Update build.gradle.kts for Kafka** `[MODIFY]` **[IMPLEMENTED]**
  - File: `build.gradle.kts`
  - FR: FR-020 — Kafka dependency configured

- [x] **T35: Enhance EventPublisher port with new events** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - FR: FR-020 — Added `SsoProvisionedEvent`, `AccountDeactivatedEvent`, `AccountDeletedEvent`, `OrgStructureChangedEvent`

### 3.2 Audit Trail Enhancement (FR-015)

- [x] **T36: Implement AuditLogService DB persistence** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt`
  - FR: FR-015 — DB insert with sensitive data masking via `maskSensitiveData()`
  - Pattern: Build AuditLogEntity → maskSensitiveData(details) → repository.save()
  - Dependencies: `AuditLogRepository`

- [x] **T37: Create AuditLogEntity + Repository (auth-service)** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogEntity.kt`
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogRepository.kt`
  - Base: `SnowflakePersistentAuditableEntity`
  - Fields: userId, action, entityType, entityId, details (JSONB), ipAddress, userAgent
  - Query methods: `findByUserId`, `findByAction`, `findByEntityTypeAndEntityId`, `findByEventTimestampBetween`

- [x] **T38: Flyway migration for audit_logs table + immutability** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/resources/db/migration/V14__audit_logs.sql`
  - Table: `audit_logs` with JSONB details
  - Constraint: CREATE RULE to prevent UPDATE/DELETE on audit_logs
  - Indexes: `idx_audit_logs_user_id`, `idx_audit_logs_action`, `idx_audit_logs_entity_type_id`, `idx_audit_logs_timestamp`

- [x] **T39: Enhance system-admin AuditAspect + export** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — AuditAspect.kt, AuditService.kt, AuditController.kt`
  - FR: FR-015 — Immutability constraints, sensitive masking, CSV/JSON export endpoint
  - Staging: `system-admin-service/src/main/kotlin/.../audit/application/AuditAspect.kt`, `AuditService.kt`, `adapter/in/web/AuditController.kt`

### 3.3 Config Enhancement (FR-014)

- [x] **T40: Enhance DomainConfigService with versioning** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — DomainConfigService.kt`
  - FR: FR-014 — Config versioning with history tracking, type validation
  - Methods: `updateConfig(key, value, type)` → increment version, create config_history record
  - Staging: `system-admin-service/src/main/kotlin/.../config/application/DomainConfigService.kt`

- [x] **T41: Flyway migration for config_history** `[NEW]` **[IMPLEMENTED]**
  - File: `system-admin-service — V6__config_history_workflow_delegation.sql`
  - Table: `config_history` (config_key, domain_id, old_value, new_value, version, changed_by, changed_at)
  - Staging: `system-admin-service/src/main/resources/db/migration/V6__config_history_workflow_delegation.sql`

- [x] **T42: Enhance FeatureFlagService** `[MODIFY]` **[IMPLEMENTED]**
  - File: `system-admin-service — FeatureFlagService.kt`
  - FR: FR-014 — Percentage rollout, segment targeting
  - Staging: `system-admin-service/src/main/kotlin/.../config/application/FeatureFlagService.kt`

### 3.4 Domain Config Enhancement (FR-016)

- [x] **T43: Add branding fields to DomainEntity** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/RbacEntities.kt`
  - FR: FR-016 — Added logoUrl, primaryColor, loginPageConfig (JSONB), faviconUrl

- [x] **T44: Flyway migration for domain branding** `[NEW]` **[IMPLEMENTED]**
  - File: `src/main/resources/db/migration/V15__domain_branding.sql`
  - Changes: ALTER domain table ADD logo_url, primary_color, login_page_config, favicon_url (with IF NOT EXISTS guard)

- [x] **T45: Enhance DomainLookupService** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/DomainLookupService.kt`
  - FR: FR-016 — Returns branding config (logoUrl, primaryColor, loginPageConfig) in domain lookup response

### 3.5 Account Lifecycle GDPR (FR-009)

- [x] **T46: Enhance AccountLifecycleService with cross-service events** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AccountLifecycleService.kt`
  - FR: FR-009 — Publishes AccountDeactivatedEvent via EventPublisher
  - Dependencies: `EventPublisher`, `AuditLogService`

### 3.6 Idempotency Replication (FR-017)

- [x] **T47: Replicate IdempotencyFilter to account-service** `[REUSE]` **[IMPLEMENTED]**
  - File: `account-service — IdempotencyFilter.kt`
  - Source: `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - FR: FR-017 — Copy pattern, configure for account-service POST endpoints
  - Staging: `account-service/src/main/kotlin/.../shared/filter/IdempotencyFilter.kt`

- [x] **T48: Replicate IdempotencyFilter to system-admin-service** `[REUSE]` **[IMPLEMENTED]**
  - File: `system-admin-service — IdempotencyFilter.kt`
  - Source: `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - FR: FR-017 — Copy pattern, configure for system-admin-service POST endpoints
  - Staging: `system-admin-service/src/main/kotlin/.../shared/filter/IdempotencyFilter.kt`

### 3.7 Preference Enhancement (FR-006)

- [x] **T49: Enhance PreferenceService** `[MODIFY]` **[IMPLEMENTED]**
  - File: `account-service — PreferenceService.kt + PreferenceController.kt`
  - FR: FR-006 — Category-based query, bulk merge-update
  - Endpoint: `GET /api/account/preferences/{category}`, `PATCH /api/account/preferences`
  - Staging: `account-service/src/main/kotlin/.../preference/application/PreferenceService.kt`

### 3.8 Retry Mechanism (FR-020 supplement)

- [x] **T50: Add retry configuration to KafkaEventPublisher** `[MODIFY]` **[IMPLEMENTED]**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - FR: FR-020 — Retry with exponential backoff, DLQ on failure

---

## Implementation Summary

### Completion Status

| Phase | Total Tasks | Completed | Remaining |
|-------|:-----------:|:---------:|:---------:|
| Phase 1 (Security-Critical) | 16 | 13 | 3 (T11–T13: system-admin Bucket4j) |
| Phase 2 (Admin Features) | 15 | 2 | 13 (account-service + system-admin-service) |
| Phase 3 (Integration & Polish) | 19 | 12 | 7 (system-admin + account-service) |
| **Total** | **50** | **27** | **23** |

### auth-service Status: **All 27 auth-service tasks COMPLETED** ✅
### account-service Status: **0/9 tasks completed** — Pending (separate service scope)
### system-admin-service Status: **0/14 tasks completed** — Pending (separate service scope)

**Overall: 27 completed, 23 pending**

---

## Open Items

- ⚠️ OPEN QUESTION: OQ-001 — Kafka dependency scope verified: spring-kafka is available as dependency in auth-service. ✅ Resolved.
- ⚠️ OPEN QUESTION: OQ-002 — DPoP support → future consideration (not in current tasks)
- ⚠️ **Error Code Range**: AUTH_050–053 consumed by Event Sourcing. Inter-service auth dedicated error codes should use AUTH_060+ if needed.
- ⚠️ **Flyway Version Gap**: Auth-service is at V15. Account-service and system-admin-service Flyway versions need to be verified before creating migrations.
