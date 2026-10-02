# Impact Analysis: remove-domain-logic

_Generated: 2026-10-02 | Tool: GitNexus + grep_

---

## 1. Core Files (Symbols bị ảnh hưởng trực tiếp)

| Symbol | File | Action | Reason |
|--------|------|--------|--------|
| `DomainEntity` | `rbac/.../entity/RbacEntities.kt` | DELETE | Multi-domain entity không còn cần |
| `UserDomainEntity` | `rbac/.../entity/RbacEntities.kt` | DELETE | User-domain mapping |
| `DomainResourceEntity` | `rbac/.../entity/PermissionEntities.kt` | RENAME → `ResourceEntity` | Bỏ domain scope |
| `DomainRoleEntity` | `rbac/.../entity/RbacEntities.kt` | RENAME → `RoleEntity` | Bỏ domain scope |
| `GroupEntity` | `rbac/.../entity/RbacEntities.kt` | MODIFY | Drop `domainId` column |
| `PasswordPolicyEntity` | `rbac/.../entity/PasswordPolicyEntity.kt` | MODIFY | Drop `domainId`, single global policy |
| `SwitchDomainHandler` | `auth/.../command/SwitchDomainHandler.kt` | DELETE | Entire switch-domain flow removed |
| `DomainLookupService` | `auth/application/DomainLookupService.kt` | DELETE | Domain resolution |
| `DomainPort` | `auth/.../port/out/DomainPort.kt` | DELETE | Port interface |
| `DomainPersistenceAdapter` | `auth/.../persistence/DomainPersistenceAdapter.kt` | DELETE | Adapter impl |
| `TokenGenerator` | `auth/.../command/TokenGenerator.kt` | MODIFY | Remove domain claims |
| `RegisterHandler` | `auth/.../command/RegisterHandler.kt` | MODIFY | Remove domain validation |
| `RbacEngine` | `rbac/application/RbacEngine.kt` | MODIFY | Remove domainId params |
| `PasswordPolicyService` | `auth/application/PasswordPolicyService.kt` | MODIFY | Remove domainId params |

## 2. Call Tree (GitNexus upstream analysis)

### DomainEntity (d=1: 4 callers, d=2+: 12 callers)
```
DomainEntity
  ├── d=1: DomainController.createDomain (RbacControllers.kt) → DELETE
  ├── d=1: RolePermissionController.kt → MODIFY
  ├── d=1: RbacControllers.kt → MODIFY
  ├── d=1: Repositories.kt → MODIFY (remove DomainRepository)
  └── d=2: (12 transitive callers via controllers)
```

### TokenGenerator (d=1: 2 callers)
```
TokenGenerator
  ├── d=1: BuildAuthResponseHandler.kt → MODIFY (update call)
  └── d=1: CqrsAuthController.kt → MODIFY (update call)
```

### RbacEngine (d=1: 4 callers, d=2: 5 callers)
```
RbacEngine
  ├── d=1: AuthService.kt → MODIFY (bỏ domainId)
  ├── d=1: AccountLifecycleService.kt → MODIFY (bỏ domainId)
  ├── d=1: RbacControllers.kt → MODIFY
  ├── d=1: InternalApiController.kt → MODIFY (bỏ domainId param)
  ├── d=2: TokenController.kt → MAY need update
  ├── d=2: SsoController.kt → MAY need update
  ├── d=2: MfaController.kt → MAY need update
  ├── d=2: AuthController.kt → MAY need update
  └── d=2: AccountLifecycleController.kt → MAY need update
```

## 3. Blast Radius Summary

| Depth | Count | Risk |
|-------|-------|------|
| d=1 (direct) | 10 files | 🟡 Medium |
| d=2 (indirect) | ~17 files | 🟢 Low (mostly parameter changes) |
| d=3 (transitive) | ~5 files | 🟢 Low (compile-time detection) |
| **Total unique** | **~32 backend files** | **🟡 Medium overall** |

> Risk mitigated: All changes are REMOVAL/SIMPLIFICATION (not adding new behavior), so compile-time errors will catch most issues.

## 4. Reuse Map

| Pattern | Decision | Reason |
|---------|----------|--------|
| BaseController/AdminController hierarchy | REUSE | No changes needed to base classes |
| AuthException hierarchy | REUSE | No domain-specific exceptions |
| GlobalExceptionHandler | REUSE | No changes needed |
| Event publishing (KafkaEventPublisher) | MODIFY | Remove domainId from event payloads |
| RBAC permission chain | MODIFY | Simplify, remove domain filter |
| PasswordPolicyService validator cache | MODIFY | ConcurrentHashMap<Long,V> → single volatile |

## 5. Context Snapshot

```
Classification: MAINTENANCE
Approach: Full Removal + Global RBAC (brainstorm decision)
Cut-over: Yes (no transitional phase — system not in production)
Downstream: notification-service (0 refs), account-service (0 refs) ✅ safe
Password Policy: 1 Global Policy (brainstorm Q1 resolved)
Config Versioning: NOT related to domain RBAC (brainstorm Q2 resolved)
```
