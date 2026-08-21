# TODO / Uncover: erp-iam-system

_Generated: 2026-08-25_

## Pre-existing TODOs (NOT from this feature)

> These TODOs existed before erp-iam-system and are tracked separately.

| File | Line | TODO | Origin |
|------|------|------|--------|
| `VersionedAuditableEntity.kt` | L16 | Move to base-model as Task 23 | base-core refactoring |
| `SwitchDomainHandler.kt` | L32 | Verify user is member of target domain | auth-core-features |
| `MfaService.kt` | L48, L208 | Dispatch actual SMS/Email via notification | notification-service dependency |
| `CaffeinePermissionCache.kt` | L18 | Wrap with Redis L2 in MultiTierPermissionCache | ✅ Resolved by Task R01/R02 |
| `TokenStorePersistenceAdapter.kt` | L47 | Add custom query for batch revocation | auth-core-features |
| `PermissionChangedConsumer.kt` | L31 | Parse JSON and invalidate specific user+domain | auth-core-features |
| `CqrsAuthController.kt` | L208 | Trigger password reset email | notification-service dependency |
| `AuthController.kt` | L76 | Trigger password reset email | notification-service dependency |

## New Observations from erp-iam-system

1. **Notification Service Dependency**: MFA recovery codes (FR-001) and password reset features depend on a notification service that doesn't exist yet. Currently logs-only for SMS/Email dispatch.
2. **AuditLogService persistence**: Task 2.8 replaced the TODO with EventPublisher-based persistence, but a dedicated `AuditLogEntity` + JPA repository in auth-service's own DB might be desired for local query support (currently sends events to system-admin-service).
3. **Kafka broker runtime**: Task 4.1 changed `spring-kafka` from `compileOnly` to `implementation`. Kafka broker MUST be available in all environments now.

## Edge Cases Worth Testing

- Department tree max depth (10 levels) enforcement
- Workflow escalation scheduler idempotency under concurrent execution
- MFA recovery code race condition (concurrent verify + regenerate)
- Device trust TTL boundary conditions
- Feature flag percentage rollout consistency under concurrent reads
