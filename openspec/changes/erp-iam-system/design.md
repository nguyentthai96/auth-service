# Design: erp-iam-system

_Generated: 2025-07-15_
_Profile: Non-Financial | N/A (no factory) | EXTEND_
_Direction: Capability-Priority Enhancement with Event-Driven Redis Backbone_

---

## 1. Architecture Design

### 1.1 System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                       │
│    [Web SPA]      [Mobile App]     [API Partner]        │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    API GATEWAY                          │
│  • JWT Validation   • Rate Limiting (Bucket4j ← Redis) │
│  • API Key Auth     • Routing                          │
└───┬─────────────────┬───dis)
│   │       ├── http/          # External HTTP clients
│   │       └── event/         # Event publishers
│   ├── application/           # Services, handlers, use cases
│   │   ├── command/           # CQRS commands + handlers
│   │   ├── query/             # CQRS queries
│   │   └── port/              # Port interfaces (in/out)
│   └── domain/
│       ├── model/             # Domain models
│       └── service/           # Domain services
└── shared/
    ├── config/                # Spring configs
    ├── exception/             # Error codes, exceptions, controller advice
    ├── security/              # Security filters
    ├── filter/                # HTTP filters (idempotency, logging)
    ├── persistence/           # Base entities (TreeEntity, VersionedEntity)
    ├── cache/                 # AbstractTwoTierCache
    ├── audit/                 # AuditLogService
    └── i18n/                  # DatabaseMessageSource
```

---

## 2. Component Design

### 2.1 auth-service Components

#### 2.1.1 MFA Enhancement (FR-001, FR-002)

**Modified Files:**
- `MfaService.kt` — Add recovery codes management
- `MfaController.kt` — Add recovery code endpoints
- `MfaDtos.kt` — Add recovery code request/response DTOs

**New Files:**
- `MfaRecoveryCodeEntity.kt` — `auth.adapter.out.persistence.entity`
- `MfaRecoveryCodeRepository.kt` — `auth.adapter.out.persistence.repository`

**Design:**
```
MfaController
├── POST /mfa/recovery-codes → MfaService.generateRecoveryCodes()
│   ├── Generate 10 random codes (16 chars, alphanumeric)
│   ├── Hash each code (SHA-256)
│   ├── Store hashed codes in mfa_recovery_codes table
│   └── Return plain codes (show-once)
├── POST /mfa/recovery-codes/verify → MfaService.verifyRecoveryCode()
│   ├── Hash input → match against stored hashes
│   ├── Mark used code (single-use)
│   └── Promote session (same as TOTP verify)
└── POST /mfa/recovery-codes/regenerate → MfaService.regenerateRecoveryCodes()
    ├── Delete all existing codes
    └── Generate new 10 codes
```

**Trusted Device MFA Skip (FR-002 + FR-007 integration):**
```
LoginHandler.handle(command)
├── AuthService.authenticate(username, password)
├── MfaService.isMfaEnabled(userId)
│   └── true → check trusted device
│       ├── REST call: GET /api/internal/devices/{userId}/trusted?fingerprint={hash}
│       ├── trusted → skip MFA → issue full JWT
│       └── not trusted → issue partial JWT → /verify-2fa flow
└── Issue full JWT (MFA disabled or trusted device)
```

#### 2.1.2 Event Publisher & Kafka Migration (FR-020)

**Modified Files:**
- `EventPublisher.kt` (port) — Add new event types
- `SpringEventPublisher.kt` — Delegate to Kafka adapter
- `build.gradle.kts` — `compileOnly` → `implementation` for spring-kafka

**New Files:**
- `KafkaEventPublisher.kt` — `auth.adapter.out.event`
- `KafkaConfig.kt` — `shared.config`

**Design:**
```
EventPublisher (port interface)
├── publishUserRegistered(event: UserRegisteredEvent)
├── publishSsoProvisioned(event: SsoProvisionedEvent)
├── publishAccountDeactivated(event: AccountDeactivatedEvent)
├── publishAccountDeleted(event: AccountDeletedEvent)
└── publishPermissionChanged(event: PermissionChangedEvent)

KafkaEventPublisher implements EventPublisher
├── KafkaTemplate<String, DomainEvent>
├── Retry: max 3, exponential backoff (1s, 2s, 4s)
├── DLQ: topic.DLT suffix
├── Serialization: JSON (Jackson)
└── Topics:
    ├── iam.user.registered → account-service
    ├── iam.user.sso_provisioned → account-service
    ├── iam.permission.changed → system-admin-service
    ├── acct.lifecycle.deactivated → auth-service
    └── system.audit.events → centralized audit
```

#### 2.1.3 Inter-service Auth (FR-021)

**Modified Files:**
- `ServiceTokenService.kt` — Enhance token generation + validation
- `SecurityConfig.kt` — Add internal endpoint filter chain

**New Files:**
- `ServiceAuthFilter.kt` — `shared.security`
- `InternalApiController.kt` — `auth.adapter.in.web`

**Design:**
```
ServiceAuthFilter extends OncePerRequestFilter
├── Match: /api/internal/**
├── Extract: Authorization: Bearer {serviceJWT}
├── Validate: ServiceTokenService.validateServiceToken(token)
│   ├── Verify JWT signature (shared secret)
│   ├── Check claims: service_name, scope=INTERNAL
│   └── Throw AUTH_050/051/052 on failure
└── Set SecurityContext with SERVICE authority

SecurityConfig
├── FilterChain 1: /api/internal/** → ServiceAuthFilter
└── FilterChain 2: /api/** → existing JWT filter
```

#### 2.1.4 Timeout & Circuit Breaker (FR-019)

**Modified Files:**
- `HttpClientConfig.kt` — Add timeout + circuit breaker config
- `HttpSsoGateway.kt` — Add @CircuitBreaker annotation
- `HttpCaptchaGateway.kt` — Add @CircuitBreaker annotation

**Design:**
```
HttpClientConfig
├── RestTemplate bean
│   ├── connectTimeout: 5s (configurable)
│   ├── readTimeout: 10s (configurable)
│   └── writeTimeout: 10s (configurable)
└── Circuit Breaker (Resilience4j)
    ├── failureRateThreshold: 50%
    ├── slidingWindowSize: 10
    ├── waitDurationInOpenState: 30s
    ├── permittedCallsInHalfOpen: 1
    └── Fallback: throw ServiceUnavailableException
```

#### 2.1.5 Audit Persistence (FR-015 auth-service portion)

**Modified Files:**
- `AuditLogService.kt` — Implement DB persistence (replace TODO)

**New Files:**
- `AuditLogEntity.kt` — `shared.audit`
- `AuditLogRepository.kt` — `shared.audit`

**Design:**
```
AuditLogService.logEvent(userId, action, entityType, entityId, details)
├── Build AuditLogEntity
│   ├── userId, action (enum), entityType, entityId
│   ├── details: JSONB
│   ├── ipAddress: from request
│   ├── userAgent: from request
│   ├── timestamp: Instant.now()
│   └── maskSensitiveData(details) ← NEW
├── auditLogRepository.save(entity) ← NEW (was TODO: log-only)
└── log.info("AUDIT ...") (keep existing structured log)
```

### 2.2 account-service Components

#### 2.2.1 Profile Enhancement (FR-005)

**Modified Files:**
- `ProfileService.kt` — Add verification flow, avatar, completeness
- `ProfileController.kt` — Add new endpoints
- `ProfileDtos.kt` — Add new DTOs

**New Files:**
- `ProfileVerificationEntity.kt` — `profile.adapter.out.persistence.entity`

**Design:**
```
ProfileService
├── updateProfile(dto) — existing, enhance validation
├── uploadAvatar(userId, file) — NEW
│   ├── Validate: max 5MB, formats jpg/png/webp
│   ├── Store path (filesystem or S3 — configurable)
│   └── Update UserProfileEntity.avatarUrl
├── initiateEmailVerification(userId) — NEW
│   ├── Generate OTP → REST call to auth-service /api/internal/otp/send
│   └── Create ProfileVerificationEntity (pending)
├── confirmVerification(userId, code) — NEW
│   ├── Verify OTP → REST call to auth-service
│   └── Mark contact as verified
└── getProfileCompleteness(userId) — NEW
    ├── Calculate: fields filled / total fields * 100
    └── Return score + missing fields list
```

#### 2.2.2 Device Enhancement (FR-007)

**Modified Files:**
- `DeviceService.kt` — Add trusted device management
- `DeviceController.kt` — Add trust/untrust endpoints
- `DeviceDtos.kt` — Add DTOs

**Design:**
```
DeviceService
├── trustDevice(userId, fingerprint) — NEW
│   ├── Validate: max 5 trusted devices
│   ├── Set trustedAt, trustedExpiry (now + 30 days)
│   └── Publish event: device.trusted
├── untrustDevice(userId, deviceId) — NEW
│   ├── Clear trustedAt, trustedExpiry
│   └── Publish event: device.untrusted
├── isDeviceTrusted(userId, fingerprint) — NEW (internal API)
│   ├── Lookup by userId + fingerprint
│   ├── Check trustedExpiry > now
│   └── Return boolean
└── cleanupExpiredTrust() — Scheduled
    └── Clear expired trusted flags (trustedExpiry < now)
```

### 2.3 system-admin-service Components

#### 2.3.1 Menu Permission Enhancement (FR-010)

**Modified Files:**
- `MenuPermissionService.kt` — Add button-level, role assignment, user override

**New Entities (Flyway migration):**
- `role_menu_permissions` table — role ↔ menu item mapping
- `user_menu_overrides` table — user-level permission overrides

**Design:**
```
MenuPermissionService
├── getUserMenuTree(userId, domainId) — ENHANCED
│   ├── Check Redis cache: user:{userId}:menu:{domainId}
│   ├── Cache miss →
│   │   ├── Load user roles (REST → auth-service /api/internal/permissions/{userId})
│   │   ├── Load role_menu_permissions
│   │   ├── Load user_menu_overrides
│   │   ├── Merge: user override > role permission
│   │   ├── Filter tree (hide unauthorized + BUTTON type in navigation)
│   │   ├── Build tree via TreeBuilder.buildTree()
│   │   └── Cache result (Redis TTL 5min)
│   └── Return cached/built tree
├── assignRolePermissions(roleId, menuIds) — NEW
├── setUserOverride(userId, menuId, action) — NEW (GRANT/DENY)
└── invalidateUserMenuCache(userId) — NEW
    └── Delete Redis key: user:{userId}:menu:*
    └── Triggered by Kafka: iam.permission.changed
```

#### 2.3.2 Organization Enhancement (FR-011)

**Modified Files:**
- `OrganizationService.kt` — Add user transfer, org chart
- `PositionService.kt` — Add department head assignment
- `DepartmentController.kt` — Add new endpoints

**Design:**
```
OrganizationService
├── transferUser(userId, fromDeptId, toDeptId, toPositionId) — NEW
│   ├── Validate: user exists in source department
│   ├── Validate: target position available
│   ├── Update UserPositionEntity
│   ├── AuditService.log(ADMIN_TRANSFER_USER)
│   └── Publish: system.org.events (Kafka)
├── getOrgChart(domainId) — NEW
│   ├── Recursive CTE query: department tree with user counts
│   ├── Include department heads
│   └── Return tree structure
└── assignDepartmentHead(deptId, userId) — NEW
    ├── Validate: user in department
    └── Set DepartmentEntity.headUserId
```

#### 2.3.3 API Partner Enhancement (FR-012)

**Modified Files:**
- `ApiPartnerService.kt` — Add Bucket4j, key lifecycle, IP whitelist
- `ApiUsageController.kt` — Add usage dashboard

**New Files:**
- `RateLimitConfig.kt` — Bucket4j configuration entity/service

**Design:**
```
ApiPartnerService
├── generateApiKey(partnerId) — ENHANCED
│   ├── Generate: ntt_pk_{random} or ntt_sk_{random}
│   ├── Hash: SHA-256
│   ├── Store hash + metadata
│   ├── Return plain key (show-once)
│   └── Max keys check (default 5 per partner)
├── rotateApiKey(partnerId, keyId) — NEW
│   ├── Revoke old key
│   └── Generate new key (same partner)
├── configureRateLimit(partnerId, config) — NEW
│   ├── Persist rate limit config (requests/min, burst)
│   └── Push to Redis: rate_limit:bucket4j:{keyHash}
│       └── Bucket4j ProxyManager reads this at Gateway
├── updateIpWhitelist(partnerId, ips) — NEW
│   ├── Validate IPs (CIDR notation supported)
│   └── Persist + push to Redis
└── getUsageDashboard(partnerId, period) — tar_url VARCHAR(500);
ALTER TABLE user_profiles ADD COLUMN completeness_score INT DEFAULT 0;

CREATE TABLE profile_verifications (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,  -- EMAIL, PHONE
    target VARCHAR(255) NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

-- V5: Trusted device fields
ALTER TABLE user_devices ADD COLUMN trusted_at TIMESTAMP;
ALTER TABLE user_devices ADD COLUMN trusted_expiry TIMESTAMP;
```

### 3.3 system-admin-service (Flyway V5+)

```sql
-- V5: Role-menu permissions
CREATE TABLE role_menu_permissions (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(id),
    created_at TIMESTAMP NOT NULL,
    created_by BIGINT,
    UNIQUE(role_id, menu_item_id)
);

-- V6: User menu overrides
CREATE TABLE user_menu_overrides (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(id),
    action VARCHAR(10) NOT NULL,  -- GRANT, DENY
    created_at TIMESTAMP NOT NULL,
    created_by BIGINT,
    UNIQUE(user_id, menu_item_id)
);

-- V7: Config versioning
ALTER TABLE domain_configs ADD COLUMN version INT DEFAULT 1;
CREATE TABLE config_history (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    domain_id BIGINT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    version INT NOT NULL,
    changed_by BIGINT,
    changed_at TIMESTAMP NOT NULL
);

-- V8: API Partner rate limit
ALTER TABLE api_partners ADD COLUMN rate_limit_requests_per_minute INT DEFAULT 100;
ALTER TABLE api_partners ADD COLUMN rate_limit_burst INT DEFAULT 20;
```

---

## 4. Inter-Service Communication

### 4.1 Synchronous (REST — Queries)

| From | To | Endpoint | Purpose |
|------|-----|----------|---------|
| system-admin | auth | `GET /api/internal/permissions/{userId}` | Get user roles for menu filtering |
| account | auth | `GET /api/internal/users/{userId}` | Get auth status for lifecycle |
| auth | account | `GET /api/internal/devices/{userId}/trusted` | Check trusted device for MFA skip |

### 4.2 Asynchronous (Kafka — State Changes)

| Producer | Topic | Consumer | Payload |
|----------|-------|----------|---------|
| auth | `iam.user.registered` | account | `{userId, email, fullName, domainId}` |
| auth | `iam.user.sso_provisioned` | account | `{userId, provider, email, displayName}` |
| auth | `iam.permission.changed` | system-admin | `{userId, roleIds, action}` |
| account | `acct.lifecycle.deactivated` | auth | `{userId, reason, timestamp}` |
| account | `acct.lifecycle.deleted` | auth | `{userId, timestamp}` |
| system-admin | `system.audit.events` | centralized | `{actor, action, entity, diff}` |

---

## 5. Caching Strategy

| Cache Key Pattern | TTL | L1 (Caffeine) | L2 (Redis) | Service |
|-------------------|-----|:---:|:---:|---------|
| `perm:{userId}:{domainId}` | 30s / 5min | ✅ | ✅ | auth |
| `role:{userId}:{domainId}` | 30s / 5min | ✅ | ✅ | auth |
| `user:{userId}:menu:{domainId}` | — / 5min | ❌ | ✅ | system-admin |
| `rate_limit:bucket4j:{keyHash}` | — / — | ❌ | ✅ | Gateway |
| `otp:{type}:{userId}` | — / 5min | ❌ | ✅ | auth |
| `domain:config:{domainCode}` | 30s / 10min | ✅ | ✅ | system-admin |

---

## 6. Error Code Allocation

| Range | Service | Feature |
|-------|---------|---------|
| AUTH_001–010 | auth | Core Auth |
| AUTH_011–021 | auth | Auth Core Features (MFA, SSO, etc.) |
| AUTH_030–039 | auth | E2EE |
| AUTH_040–044 | auth | Anonymous Session |
| AUTH_050–052 | auth | Inter-service Auth [NEW] |
| AUTH_053 | auth | Audit Export [NEW] |
| ACCT_001–008 | account | Account Management |
| SYS_001–018 | system-admin | System Administration |

---

## 7. Security Design

### 7.1 Authentication Flow

```
Client → API Gateway → Service
  │
  ├── Public endpoints: no auth (login, register, SSO callback)
  ├── User endpoints: JWT Bearer token (validated by JwtAuthFilter)
  ├── Admin endpoints: JWT + ROLE_ADMIN check
  ├── Internal endpoints: Service JWT (validated by ServiceAuthFilter)
  └── API Partner endpoints: X-API-Key header (validated at Gateway)
```

### 7.2 Data Protection

| Data | Protection | Where |
|------|-----------|-------|
| Passwords | Argon2id hash | auth-service UserEntity |
| TOTP secrets | AES-256 encryption | auth-service (Tink) |
| API keys | SHA-256 hash (show-once) | system-admin ApiPartnerEntity |
| Recovery codes | SHA-256 hash (show-once) | auth-service MfaRecoveryCodeEntity |
| Audit log data | Immutable (no UPDATE/DELETE) | DB rules |
| Sensitive fields in audit | Masked (`***`) | AuditService.maskSensitiveFields() |
