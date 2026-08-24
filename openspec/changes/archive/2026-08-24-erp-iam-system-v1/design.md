# Design: erp-iam-system

_Generated: 2026-08-24_
_Profile: Non-Financial | N/A (no factory) | NEWBUILD_
_Brainstorm Direction: Event-Driven & Redis-Backed Decentralized Authorization_

---

## 1. Architecture Overview

### 1.1 Pattern
- **Architecture**: Clean Architecture (Hexagonal) — consistent across all 3 services.
- **Package Structure**: `adapter/in/web`, `adapter/in/kafka`, `adapter/out/persistence`, `adapter/out/cache`, `adapter/out/event`, `application`, `domain/model`.
- **Authorization Model**: Lean JWT (userId, tenantId, roleIds) + runtime permission resolution via Redis.
- **Communication**: Hybrid — REST sync for queries, Kafka async for state change events.
- **Caching**: Two-tier Caffeine L1 (30s TTL) + Redis L2 (5-30min TTL).

### 1.2 Service Architecture Diagram

```text
                    ┌─────────────────────────────────────────────┐
                    │              CLIENT LAYER                    │
                    │  [Web SPA]    [Mobile]    [API Partner]      │
                    └──────────────────┬──────────────────────────┘
                                       │
                    ┌──────────────────▼──────────────────────────┐
                    │           API GATEWAY                        │
                    │  • JWT Validation (RS256 public key)         │
                    │  • API Key Auth (SHA-256 lookup → Redis)     │
                    │  • Rate Limiting (Bucket4j ←── Redis)       │
                    │  • Route to target service                   │
                    └──┬────────────┬────────────┬───────────────┘
                       │            │            │
         ┌─────────────▼──┐  ┌─────▼──────┐  ┌──▼──────────────┐
         │ auth-service    │  │ account-   │  │ system-admin-   │
         │ (PostgreSQL)    │  │ service    │  │ service         │
         │                 │  │(PostgreSQL)│  │ (PostgreSQL)    │
         │ Auth/JWT/MFA    │  │            │  │                 │
         │ SSO/OAuth2      │  │ Profile    │  │ Menu Permission │
         │ RBAC/PBAC       │  │ Device     │  │ Organization    │
         │ Password Policy │  │ Session    │  │ API Partner     │
         │ E2EE            │  │ Preference │  │ Workflow        │
         │ Events (Kafka)  │  │ Lifecycle  │  │ Audit / Config  │
         └───────┬────────┘  └─────┬──────┘  └───────┬─────────┘
                 │                  │                   │
                 └──────────┬───────┴──────────────────┘
                            │
              ┌─────────────▼──────────────────────────┐
              │          INFRASTRUCTURE                  │
              │  ┌─────────┐  ┌────────┐  ┌──────────┐ │
              │  │ Redis   │  │ Kafka  │  │PostgreSQL│ │
              │  │ Cache   │  │ Events │  │ 3x DBs   │ │
              │  │ Rate    │  │ DLQ    │  │          │ │
              │  │ Session │  │        │  │          │ │
              │  └─────────┘  └────────┘  └──────────┘ │
              └────────────────────────────────────────┘
```

---

## 2. Component Design

### 2.1 auth-service (Existing — EXTEND)

#### Existing Components (Key)
| Component | Package | Responsibility |
|-----------|---------|---------------|
| `AuthService` | `auth.application` | Core auth orchestrator |
| `MfaService` | `auth.application` | MFA verification, enable/disable |
| `JwtService` | `auth.application` | JWT generation/validation (RS256) |
| `SsoAdapter` | `auth.application` | OAuth2/SSO integration |
| `LoginSessionService` | `auth.application` | Session tracking + cleanup |
| `EventPublisher` | `auth.application.port.out` | Domain event port |
| `SpringEventPublisher` | `auth.adapter.out.event` | Event stub (logs only) |
| `RbacEngine` | `rbac.application` | Role-based access control engine |
| `PolicyEvaluator` | `pbac.application` | Policy-based access control evaluator |
| `AuditLogService` | `shared.audit` | Security audit logging |

#### New/Enhanced Components
| Component | Package | Action | FR |
|-----------|---------|--------|-----|
| `KafkaEventPublisher` | `auth.adapter.out.event` | [NEW] Kafka adapter for EventPublisher port | FR-020 |
| `ServiceTokenService` | `auth.application` | [NEW] Service-level JWT for inter-service auth | FR-021 |
| `ServiceAuthFilter` | `auth.adapter.in.web.filter` | [NEW] Filter for `/api/internal/**` endpoints | FR-021 |
| `InternalApiController` | `auth.adapter.in.web` | [NEW] Internal endpoints for cross-service queries | FR-021 |
| `IdempotencyFilter` | `shared.filter` | [NEW] Idempotency key handling via Redis | FR-017 |
| `TimeoutCircuitBreakerConfig` | `shared.config` | [MODIFY] Add circuit breaker to HttpClientConfig | FR-019 |

#### Event Flow (auth-service as Producer)
```text
RegisterHandler.handle()
  └── EventPublisher.publish(UserRegisteredEvent)
        └── [Phase 1-2] SpringEventPublisher → log
        └── [Phase 3+]  KafkaEventPublisher → topic: iam.user.registered

SsoAdapter.autoProvision()
  └── EventPublisher.publish(SsoProvisionedEvent)
        └── KafkaEventPublisher → topic: iam.user.sso_provisioned
```

### 2.2 account-service (NEWBUILD + EXTEND)

#### Package Structure
```text
com.ntt.accountservice/
├── profile/
│   ├── adapter/in/web/         ProfileController.kt, ProfileDtos.kt
│   ├── adapter/in/kafka/       ProfileKafkaListener.kt (existing)
│   ├── adapter/out/persistence/ UserProfileEntity.kt, UserContactEntity.kt
│   └── application/            ProfileService.kt
├── device/
│   ├── adapter/in/web/         DeviceController.kt
│   ├── adapter/out/persistence/ UserDeviceEntity.kt
│   └── application/            DeviceService.kt
├── session/
│   ├── adapter/in/web/         SessionController.kt (existing)
│   ├── adapter/out/cache/      SessionRedisAdapter.kt (existing)
│   └── application/            SessionService.kt (existing)
├── preference/
│   ├── adapter/in/web/         PreferenceController.kt
│   ├── adapter/out/persistence/ UserPreferenceEntity.kt
│   └── application/            PreferenceService.kt
├── lifecycle/
│   ├── adapter/in/web/         LifecycleController.kt
│   ├── adapter/out/persistence/ AccountDeletionRequestEntity.kt (existing)
│   ├── adapter/out/event/      KafkaLifecyclePublisher.kt
│   └── application/            AccountLifecycleService.kt (existing), DataExportService.kt (existing)
└── shared/
    ├── config/                 SecurityConfig.kt, RedisConfig.kt, KafkaConfig.kt
    ├── exception/              AccountErrorCode.kt, AccountExceptions.kt
    └── persistence/            (reuse base-core entities)
```

#### Key Components
| Component | Responsibility | Status |
|-----------|---------------|--------|
| `ProfileService` | CRUD user profile, email/phone verification | NEWBUILD |
| `ProfileKafkaListener` | Consume user.registered → create default profile | EXTEND (exists) |
| `DeviceService` | Device tracking, trust management, remote logout | NEWBUILD |
| `PreferenceService` | Key-value preferences, merge-update semantics | NEWBUILD |
| `SessionService` | Redis-backed active session tracking | Existing |
| `AccountLifecycleService` | Deactivate, delete, export with Kafka events | EXTEND |
| `DataExportService` | GDPR data export aggregation | EXTEND |

### 2.3 system-admin-service (NEWBUILD + EXTEND)

#### Package Structure
```text
com.ntt.sysadminservice/
├── menu/
│   ├── adapter/in/web/         MenuController.kt (existing)
│   ├── adapter/out/persistence/ MenuEntities.kt (existing — 4 entities)
│   ├── adapter/out/cache/      MenuPermissionCacheAdapter.kt (existing)
│   └── application/            MenuPermissionService.kt (existing)
├── organization/
│   ├── adapter/in/web/         DepartmentController.kt, PositionController.kt
│   ├── adapter/out/persistence/ DepartmentEntity.kt, PositionEntity.kt, UserPositionEntity.kt
│   └── application/            OrganizationService.kt, PositionService.kt
├── apipartner/
│   ├── adapter/in/web/         ApiPartnerController.kt (existing), ApiUsageController.kt (existing)
│   ├── adapter/out/persistence/ ApiPartnerEntities.kt (existing — 5 entities)
│   ├── adapter/out/cache/      RateLimitRedisAdapter.kt
│   └── application/            ApiKeyService.kt (existing), ApiPartnerService.kt
├── workflow/
│   ├── adapter/in/web/         WorkflowController.kt
│   ├── adapter/out/persistence/ WorkflowEntities.kt (Definition, Step, Instance, Action)
│   └── application/            WorkflowEngine.kt, WorkflowService.kt
├── config/
│   ├── adapter/in/web/         DomainConfigController.kt (existing)
│   ├── adapter/out/persistence/ DomainConfigEntities.kt (existing), FeatureFlagEntity.kt
│   └── application/            DomainConfigService.kt (existing), FeatureFlagService.kt
├── audit/
│   ├── adapter/in/web/         AuditController.kt
│   ├── adapter/out/persistence/ AuditLogEntity.kt, AuditLogRepository.kt
│   └── application/            AuditService.kt, AuditAspect.kt
└── shared/
    ├── config/                 SecurityConfig.kt, RedisConfig.kt, KafkaConfig.kt
    ├── exception/              SysAdminErrorCode.kt, SysAdminExceptions.kt
    └── persistence/            TreeEntity.kt (abstract base for menu + department)
```

#### Key Components
| Component | Responsibility | Status | FR |
|-----------|---------------|--------|-----|
| `MenuPermissionService` | Menu tree CRUD, permission algorithm, Redis cache | Existing | FR-010 |
| `MenuPermissionCacheAdapter` | Two-tier cache for menu tree | Existing | FR-010 |
| `OrganizationService` | Department tree CRUD, cycle detection, max depth | NEWBUILD | FR-011 |
| `PositionService` | Position CRUD, user assignment | NEWBUILD | FR-011 |
| `ApiKeyService` | API key lifecycle, SHA-256 hash, Redis sync | Existing | FR-012 |
| `ApiPartnerService` | Partner onboarding, quota management | EXTEND | FR-012 |
| `WorkflowEngine` | State machine, condition evaluation, escalation | NEWBUILD | FR-013 |
| `WorkflowService` | Workflow CRUD, instance management | NEWBUILD | FR-013 |
| `DomainConfigService` | System config CRUD, versioning | Existing | FR-014 |
| `FeatureFlagService` | Feature flag management per domain | NEWBUILD | FR-014 |
| `AuditService` | Immutable audit log, search, export | NEWBUILD | FR-015 |
| `AuditAspect` | AOP interceptor for admin CRUD audit logging | NEWBUILD | FR-015 |

---

## 3. Database Design

### 3.1 auth-service (auth_db — Existing + Extensions)

Existing schema maintained. New additions:

```sql
-- FR-021: Inter-service JWT tracking
CREATE TABLE service_tokens (
    id BIGINT PRIMARY KEY,           -- Snowflake ID
    service_name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,  -- SHA-256 hash
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### 3.2 account-service (account_db — NEWBUILD)

```sql
-- FR-005: User Profile
CREATE TABLE user_profiles (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,   -- Reference to auth-service user
    display_name VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    address TEXT,
    timezone VARCHAR(50) DEFAULT 'UTC',
    locale VARCHAR(10) DEFAULT 'en',
    avatar_url VARCHAR(500),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- FR-005: User Contact (with verification)
CREATE TABLE user_contacts (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_type VARCHAR(20) NOT NULL,   -- EMAIL, PHONE
    contact_value VARCHAR(200) NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    verification_code VARCHAR(10),
    verification_expires_at TIMESTAMP,
    is_primary BOOLEAN DEFAULT FALSE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(user_id, contact_type, contact_value)
);

-- FR-006: User Preferences
CREATE TABLE user_preferences (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    value_type VARCHAR(20) DEFAULT 'STRING',  -- STRING, JSON, NUMBER, BOOLEAN
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(user_id, category, preference_key)
);

-- FR-007: User Devices
CREATE TABLE user_devices (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_fingerprint VARCHAR(128) NOT NULL,
    device_name VARCHAR(200),
    device_type VARCHAR(50),           -- DESKTOP, MOBILE, TABLET
    browser VARCHAR(100),
    os VARCHAR(100),
    ip_address VARCHAR(45),
    trusted BOOLEAN DEFAULT FALSE,
    trusted_until TIMESTAMP,
    last_used_at TIMESTAMP,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(user_id, device_fingerprint)
);
```

### 3.3 system-admin-service (sysadmin_db — NEWBUILD + Existing)

```sql
-- FR-011: Organization - Department Tree
CREATE TABLE departments (
    id BIGINT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    parent_id BIGINT REFERENCES departments(id),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    level INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    manager_user_id BIGINT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    UNIQUE(domain_id, code)
);

-- FR-011: Organization - Positions
CREATE TABLE positions (
    id BIGINT PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES departments(id),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(department_id, code)
);

-- FR-011: User Position Assignment
CREATE TABLE user_positions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL REFERENCES positions(id),
    department_id BIGINT NOT NULL REFERENCES departments(id),
    is_primary BOOLEAN DEFAULT FALSE,
    start_date DATE NOT NULL,
    end_date DATE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(user_id, position_id)
);

-- FR-013: Workflow Definitions (immutable versioned)
CREATE TABLE workflow_definitions (
    id BIGINT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    entity_type VARCHAR(100) NOT NULL,  -- e.g., 'API_PARTNER', 'ROLE_CHANGE'
    name VARCHAR(200) NOT NULL,
    description TEXT,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT, ACTIVE, DEPRECATED
    conditions JSONB,                    -- routing conditions (PBAC pattern)
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(domain_id, entity_type, version)
);

-- FR-013: Workflow Steps
CREATE TABLE workflow_steps (
    id BIGINT PRIMARY KEY,
    definition_id BIGINT NOT NULL REFERENCES workflow_definitions(id),
    step_order INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    approver_type VARCHAR(50) NOT NULL,  -- ROLE, USER, DEPARTMENT_HEAD
    approver_value VARCHAR(200),         -- role code, user id, or department id
    timeout_hours INT DEFAULT 48,
    escalation_to VARCHAR(200),          -- escalation target
    conditions JSONB,                    -- step-specific conditions
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- FR-013: Workflow Instances (state machine)
CREATE TABLE workflow_instances (
    id BIGINT PRIMARY KEY,
    definition_id BIGINT NOT NULL REFERENCES workflow_definitions(id),
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    submitted_by BIGINT NOT NULL,
    current_step INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- States: PENDING, IN_PROGRESS, APPROVED, REJECTED, ESCALATED, CANCELLED
    submitted_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- FR-013: Workflow Actions (audit trail per step)
CREATE TABLE workflow_actions (
    id BIGINT PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES workflow_instances(id),
    step_order INT NOT NULL,
    action_type VARCHAR(20) NOT NULL,  -- APPROVE, REJECT, DELEGATE, ESCALATE
    actor_user_id BIGINT NOT NULL,
    delegated_to BIGINT,
    comment TEXT,
    action_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- FR-014: Feature Flags
CREATE TABLE feature_flags (
    id BIGINT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    flag_key VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT FALSE,
    rollout_percentage INT DEFAULT 100,  -- 0-100
    user_segments JSONB,                 -- target user segments
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(domain_id, flag_key)
);

-- FR-015: Immutable Audit Log
CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY,
    domain_id BIGINT,
    user_id BIGINT,
    action VARCHAR(50) NOT NULL,        -- CREATE, UPDATE, DELETE
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT,
    old_value_json JSONB,
    new_value_json JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    correlation_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL
    -- NO updated_at, NO updated_by → immutable
);

-- Immutability constraint
CREATE RULE no_update_audit_logs AS ON UPDATE TO audit_logs DO INSTEAD NOTHING;
CREATE RULE no_delete_audit_logs AS ON DELETE TO audit_logs DO INSTEAD NOTHING;
```

---

## 4. Security Design

### 4.1 JWT Strategy (Lean JWT)
- **Claims**: `sub` (userId), `tid` (tenantId), `roles` (roleIds array), `iat`, `exp`, `jti`.
- **Algorithm**: RS256 (asymmetric — Gateway validates with public key).
- **Access Token TTL**: 15 minutes.
- **Refresh Token TTL**: 7 days.
- **Partial Token**: scope=`mfa_verify_only`, TTL=5 minutes.
- **Service Token**: scope=`internal`, issued per service.

### 4.2 Inter-Service Authentication
- **Path**: `/api/internal/**` endpoints require service JWT.
- **Phase 1**: Shared secret configured via environment variable.
- **Phase 4**: Service account auto-registration.
- **Filter**: `ServiceAuthFilter` validates service JWT before controller.

### 4.3 API Key Security
- **Format**: `ntt_pk_<random32>` (publishable), `ntt_sk_<random32>` (secret).
- **Storage**: SHA-256 hash of key (show-once pattern).
- **Rotation**: New key generated → old key grace period 24h.
- **Revocation**: Immediate via Redis Pub/Sub → Gateway invalidates.

---

## 5. Caching Design

| Data | L1 (Caffeine) | L2 (Redis) | Invalidation |
|------|:---:|:---:|---|
| User permissions | 30s | 5min | Kafka `iam.permission.changed` |
| Menu tree (per user) | 30s | 5min | Redis Pub/Sub `menu_cache_invalidated` |
| Rate limit config | N/A | Persistent | Direct write by `ApiKeyService` |
| Domain config | 30s | 30min | On-write invalidation |
| Session data | N/A | Session TTL | Explicit termination |
| OTP codes | N/A | 5min TTL | Auto-expire |
| Idempotency keys | N/A | 24h TTL | Auto-expire |

---

## 6. Event Design

### 6.1 Kafka Events (Async State Changes)
```text
auth-service (producer):
  iam.user.registered       → account-service creates profile
  iam.user.sso_provisioned  → account-service creates SSO profile

system-admin-service (producer):
  iam.permission.changed    → auth-service invalidates permission cache

account-service (producer):
  acct.lifecycle.deactivated → auth-service invalidates tokens
  acct.lifecycle.deleted     → auth-service purges user data
```

### 6.2 Redis Pub/Sub (Cache Invalidation)
```text
system-admin-service:
  channel: menu_cache_invalidated  → all instances invalidate menu cache L1
  channel: api_key_revoked         → Gateway invalidates key
```

### 6.3 Event Migration Plan
- **Phase 1-2**: `SpringEventPublisher` (local ApplicationEvent) — log only.
- **Phase 3**: Implement `KafkaEventPublisher` adapter replacing `SpringEventPublisher`.
- **Phase 4**: Enable full Kafka event streaming (`compileOnly` → `implementation`).

---

## 7. Component Mapping (FR → Component)

| FR | Primary Component | Service | Base Class | Package |
|----|-------------------|---------|-----------|---------|
| FR-001 | `MfaService` | auth | N/A | `auth.application` |
| FR-002 | `LoginHandler` | auth | N/A | `auth.application.command` |
| FR-003 | `SsoAdapter` | auth | N/A | `auth.application` |
| FR-004 | `PasswordPolicyService` | auth | N/A | `auth.application` |
| FR-005 | `ProfileService` | account | N/A | `profile.application` |
| FR-006 | `PreferenceService` | account | N/A | `preference.application` |
| FR-007 | `DeviceService` | account | N/A | `device.application` |
| FR-008 | `SessionService` / `LoginSessionService` | account/auth | N/A | `session.application` |
| FR-009 | `AccountLifecycleService` | account | N/A | `lifecycle.application` |
| FR-010 | `MenuPermissionService` | system-admin | N/A | `menu.application` |
| FR-011 | `OrganizationService` | system-admin | N/A | `organization.application` |
| FR-012 | `ApiKeyService` | system-admin | N/A | `apipartner.application` |
| FR-013 | `WorkflowEngine` | system-admin | N/A | `workflow.application` |
| FR-014 | `DomainConfigService`, `FeatureFlagService` | system-admin | N/A | `config.application` |
| FR-015 | `AuditService`, `AuditAspect` | system-admin | N/A | `audit.application` |
| FR-016 | `DomainLookupService` | auth | N/A | `auth.application` |
| FR-017 | `IdempotencyFilter` | all | N/A | `shared.filter` |
| FR-018 | `HttpLoggingFilter` | all | N/A | base-core `common-log` |
| FR-019 | `HttpClientConfig` | auth | N/A | `shared.config` |
| FR-020 | `KafkaEventPublisher` | auth | N/A | `auth.adapter.out.event` |
| FR-021 | `ServiceAuthFilter`, `ServiceTokenService` | auth | N/A | `auth.adapter.in.web.filter` |

---

## 8. Tree Structure Design

### 8.1 TreeEntity Base (Deferred — Phase 1 with DepartmentEntity)
```kotlin
@MappedSuperclass
abstract class TreeEntity<T : TreeEntity<T>> : SnowflakePersistentAuditableEntity() {
    abstract var parentId: Long?
    abstract var sortOrder: Int
    abstract var level: Int

    @Transient
    var children: MutableList<T> = mutableListOf()

    fun isRoot(): Boolean = parentId == null
}
```

### 8.2 TreeBuilder Utility
```kotlin
object TreeBuilder {
    fun <T : TreeEntity<T>> buildTree(flatList: List<T>): List<T> { ... }
    fun <T : TreeEntity<T>> detectCycle(node: T, allNodes: Map<Long, T>): Boolean { ... }
    fun <T : TreeEntity<T>> calculateLevel(node: T, allNodes: Map<Long, T>): Int { ... }
}
```

Used by: `DepartmentEntity`, optionally `MenuItemEntity` (refactor in later phase).

---

## 9. Workflow State Machine

```text
                    ┌──────────┐
                    │  PENDING  │
                    └─────┬────┘
                          │ submit
                    ┌─────▼──────────┐
           ┌────── │  IN_PROGRESS    │ ──────┐
           │       └─────┬──────────┘       │
           │             │                   │
     approve│        delegate          reject│
           │             │                   │
           │       ┌─────▼──────┐           │
           │       │  IN_PROGRESS│ (next     │
           │       │  (delegated)│  approver)│
           │       └─────┬──────┘           │
           │             │                   │
           ▼             ▼                   ▼
    ┌──────────┐  ┌──────────┐       ┌──────────┐
    │ APPROVED │  │ESCALATED │       │ REJECTED │
    └──────────┘  └──────────┘       └──────────┘
                       │
                  auto-escalation
                  (timeout)

    * CANCELLED: from any state except APPROVED/REJECTED (by submitter)
```

Guard conditions:
- `canApprove(step)`: assigned approver or delegate
- `canReject(step)`: assigned approver
- `canDelegate(step)`: assigned approver, max 1 delegation per step
- `canCancel(instance)`: submitter, status not APPROVED/REJECTED
- `isEscalated(step)`: timeout exceeded
