# Tasks: erp-iam-system

<!-- self-contained: true -->

## Phase 1: Base Core Extensions
- [x] **Task 1.1: Enhance base-security-starter**
  - File: `base-security-starter/src/main/kotlin/com/ntt/base/security/MfaFilter.kt` | Action: [NEW]
  - FR: FR-001 — Quản lý Multi-Factor Authentication
  - Dependencies: `Bucket4j`, `Redis`
- [x] **Task 1.2: Add TreeEntity base class**
  - File: `base-model/src/main/kotlin/com/ntt/base/model/TreeEntity.kt` | Action: [NEW]
  - FR: FR-011 — Cơ cấu tổ chức, Menu động
- [x] **Task 1.3: Audit Log Service AOP**
  - File: `base-core/src/main/kotlin/com/ntt/base/core/audit/AuditLogAspect.kt` | Action: [NEW]
  - FR: FR-015 — Immutable Audit Trail

## Phase 2: system-admin-service Bootstrap
- [x] **Task 2.1: Initialize Modulith module**
  - File: `system-admin-service/build.gradle.kts` | Action: [NEW]
  - Pattern: Spring Modulith
- [x] **Task 2.2: Dynamic Menu Domain**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadmin/menu/MenuController.kt` | Action: [NEW]
  - FR: FR-010 — Quản lý Menu động
- [x] **Task 2.3: API Partner Domain & Redis Rate Limit Sync**
  - File: `system-admin-service/src/main/kotlin/com/ntt/sysadmin/apipartner/ApiKeyService.kt` | Action: [NEW]
  - FR: FR-012 — API Partner Management
  - Dependencies: Redis (Bucket4j configuration syncing)

## Phase 3: account-service Bootstrap
- [x] **Task 3.1: Profile Domain & Kafka Consumer**
  - File: `account-service/src/main/kotlin/com/ntt/account/profile/ProfileKafkaListener.kt` | Action: [NEW]
  - FR: FR-005 — Quản lý User Profile
  - Dependencies: Kafka (`iam.user.sso_provisioned` event consumer)
- [x] **Task 3.2: Device & Session Domain**
  - File: `account-service/src/main/kotlin/com/ntt/account/session/SessionService.kt` | Action: [NEW]
  - FR: FR-007, FR-008 — Session & Device

## Phase 4: auth-service Upgrades
- [x] **Task 4.1: MFA & SSO Integrations (Kafka Producer)**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` | Action: [NEW]
  - FR: FR-001, FR-002
  - Dependencies: Kafka (Producer for `iam.user.sso_provisioned`)
- [x] **Task 4.2: Password Policies**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt` | Action: [NEW]
  - FR: FR-004 — Quản lý Password Policy
