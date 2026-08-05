# Impact Analysis — architecture-optimization

## 1. Core Files (Blast Radius)

### Primary Targets (MODIFY/REFACTOR)

| File | Lines | Direct Callers | Impact | Action |
|------|:-----:|:--------------:|:------:|--------|
| [AuthService.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | 307 | 4 (AuthController, SsoController, MfaController, TokenController) | 🟡 Medium | EXTRACT → 5 CommandHandlers + domain services |
| [RbacEngine.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt) | 90 | 2 (AuthService, RbacControllers) | 🟢 Low | EXTRACT → 3 QueryHandlers + RbacResolver |
| [AuthExceptions.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt) | 78 | All exception throwers | 🟡 Medium | MODIFY → extend BusinessException |
| [SsoAdapter.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | — | SsoController | 🟢 Low | MODIFY → @HttpExchange |
| [CaptchaVerifier.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt) | — | AuthService/LoginHandler | 🟢 Low | MODIFY → @HttpExchange |
| [PolicyEvaluator.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/pbac/application/PolicyEvaluator.kt) | — | PbacControllers | 🟢 Low | MODIFY → Virtual Threads executor |

### Controller Files (REWIRE)

| File | Current Dependency | Target Dependency | Impact |
|------|-------------------|-------------------|:------:|
| [AuthController.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt) | `AuthService` | `CommandBus`, `QueryBus` | 🟡 Medium |
| [SsoController.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt) | `AuthService` | `CommandBus` | 🟢 Low |
| [MfaController.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | `AuthService` | `CommandBus` | 🟢 Low |
| [TokenController.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt) | `AuthService` | `CommandBus`, `QueryBus` | 🟢 Low |
| [RbacControllers.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt) | `RbacEngine` | `QueryBus` | 🟢 Low |

---

## 2. Call Tree

### AuthService Call Tree (depth 2)

```
AuthService (307 lines, 11 deps)
├── AuthController.register()
│   └── AuthService.register()
│       ├── userRepository.existsByUsername()
│       ├── userRepository.existsByEmail()
│       ├── domainRepository.findByCodeAndActiveTrue()
│       ├── userRepository.save()
│       ├── userDomainRepository.save()
│       └── generateAuthResponse() [private]
│           ├── rbacEngine.getUserRoles()
│           ├── rbacEngine.getEffectivePermissions()
│           └── jwtService.generateAccessToken()
├── AuthController.login()
│   └── AuthService.login()
│       ├── userRepository.findByUsernameAndActiveTrue()
│       ├── passwordEncoder.matches()
│       ├── captchaVerifier.verify()
│       ├── mfaService.initiateMfa()
│       └── generateAuthResponse()
├── TokenController.refresh()
│   └── AuthService.refreshToken()
│       ├── refreshTokenRepository.findByTokenHashAndRevokedFalse()
│       └── generateAuthResponse()
├── AuthController.switchDomain()
│   └── AuthService.switchDomain()
│       ├── domainRepository.findByCodeAndActiveTrue()
│       ├── userDomainRepository.findByUserIdAndDomainIdAndActiveTrue()
│       └── generateAuthResponse()
├── SsoController.ssoCallback()
│   └── AuthService.buildAuthResponseForUser()
│       └── generateAuthResponse()
└── MfaController.verifyMfa()
    └── AuthService.buildAuthResponseForUser()
        └── generateAuthResponse()
```

### RbacEngine Call Tree (depth 2)

```
RbacEngine (90 lines, 7 deps)
├── AuthService.generateAuthResponse()
│   ├── rbacEngine.getUserRoles()
│   │   └── resolveUserRoleIds() [private]
│   │       ├── userGroupRepository.findAllByUserIdAndActiveTrue()
│   │       └── groupRoleRepository.findAllByGroupIdAndActiveTrue()
│   └── rbacEngine.getEffectivePermissions()
│       ├── resolveUserRoleIds() [shared]
│       ├── rolePermissionRepository.findAllByRoleIdAndActiveTrue()
│       ├── permissionRepository.findById() ← N+1
│       ├── domainResourceRepository.findById() ← N+1
│       └── actionRepository.findById() ← N+1
└── RbacControllers.checkPermission()
    └── rbacEngine.hasPermission()
        ├── domainResourceRepository.findByDomainIdAndCodeAndActiveTrue()
        ├── actionRepository.findByCode()
        ├── permissionRepository.findByResourceIdAndActionId()
        └── resolveUserPermissions() [shared]
```

---

## 3. Blast Radius Summary

### Per-Phase Impact

| Phase | Files Modified | Files Added | Files Deleted | Risk Level |
|:-----:|:--------------:|:-----------:|:------------:|:----------:|
| P1 (Domain) | 0 | ~20 | 0 | 🟢 ZERO blast radius |
| P2 (CQRS) | 5 controllers + build.gradle | ~15 handlers | AuthService.kt (replaced) | 🟡 Medium |
| P3 (Communication) | 2 (SsoAdapter, CaptchaVerifier) + build.gradle | ~8 (proto, gRPC, @HttpExchange) | 0 | 🟢 Low |
| P4 (Performance) | 1 (PolicyEvaluator) + application.yml | ~5 (cache adapters) | 0 | 🟢 Low |
| P5 (Testing) | 0 | ~8 test files | 0 | 🟢 None |

### Downstream Impact

| Consumer | Impact | Mitigation |
|----------|:------:|------------|
| REST API consumers | ❌ None | API endpoints unchanged |
| Kafka consumers | ❌ None (new topic added, existing unchanged) | New `iam.permission.changed` topic |
| gRPC consumers | 🆕 New | New gRPC endpoint — no existing consumers |
| JWT token format | ❌ None | Claims unchanged |

---

## 4. Reuse Map

### Reuse from base-core (MUST ADOPT)

| Component | base-core Source | Target Usage | Decision |
|-----------|-----------------|--------------|:--------:|
| `CommandBus` | [CommandBus.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/command/CommandBus.kt) | Dispatch commands | **REUSE** |
| `CommandHandler<C,R>` | [CommandHandler.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/command/CommandHandler.kt) | Handler interface | **REUSE** |
| `QueryBus` | [QueryBus.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/query/QueryBus.kt) | Dispatch queries | **REUSE** |
| `QueryHandler<Q,R>` | [QueryHandler.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/query/QueryHandler.kt) | Handler interface | **REUSE** |
| `SpringCommandBus` | [SpringCommandBus.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/SpringCommandBus.kt) | Auto-discovery impl | **REUSE** |
| `BusinessException` | [BusinessException.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/exception/BusinessException.kt) | Exception base | **REUSE** (bridge) |
| `BaseControllerAdvice` | [BaseControllerAdvice.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt) | Exception handling | **REUSE** (extend+override) |
| `ErrorCodeBase` | [ErrorCodeBase.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/exception/base/ErrorCodeBase.kt) | Error code enum | **REUSE** |
| `SnowflakePersistentAuditableEntity` | base-model | Entity base | **REUSE** (already used) |

### Extract from AuthService (MUST EXTRACT)

| Logic Block | Source | Target | Lines | Decision |
|-------------|--------|--------|:-----:|:--------:|
| `register()` | AuthService L38-73 | `RegisterUserHandler` | ~35 | **EXTRACT** |
| `login()` | AuthService L76-130 | `LoginHandler` | ~55 | **EXTRACT** |
| `refreshToken()` | AuthService L133-154 | `RefreshTokenHandler` | ~22 | **EXTRACT** |
| `switchDomain()` | AuthService L160-173 | `SwitchDomainHandler` | ~14 | **EXTRACT** |
| `revokeAllSessions()` | AuthService L263-271 | `RevokeSessionsHandler` | ~9 | **EXTRACT** |
| `buildAuthResponseForUser()` | AuthService L251-257 | `BuildAuthResponseHandler` | ~7 | **EXTRACT** |
| `generateAuthResponse()` | AuthService L175-222 | `AuthResponseBuilder` (domain svc) | ~48 | **EXTRACT** |
| `handleFailedLogin()` | AuthService L224-235 | `LoginHandler` (internal) | ~12 | **MERGE** |
| `getPrimaryDomain()` | AuthService L237-246 | `DomainLookupService` (domain svc) | ~10 | **EXTRACT** |
| `hashToken()` | AuthService L273-276 | `TokenHasher` (utility) | ~4 | **EXTRACT** |
| `hasPermission()` | RbacEngine L29-42 | `CheckPermissionHandler` | ~14 | **EXTRACT** |
| `getUserRoles()` | RbacEngine L48-53 | `GetUserRolesHandler` | ~6 | **EXTRACT** |
| `getEffectivePermissions()` | RbacEngine L59-72 | `GetPermissionsHandler` (+ N+1 fix) | ~14 | **EXTRACT** |
| `resolveUserRoleIds()` | RbacEngine L74-80 | `RbacResolver` (shared svc) | ~7 | **EXTRACT** |

### NEW (no existing equivalent)

| Component | Reason |
|-----------|--------|
| Domain models (`User`, `Permission`, `AuthToken`, VOs) | No domain layer exists |
| Outbound ports (`UserRepository`, `TokenStore`, etc.) | No port interfaces exist |
| MapStruct mappers | Not used currently |
| gRPC PermissionService | New capability |
| @HttpExchange clients | Replace RestTemplate |
| Caffeine L1 cache | New capability |
| Multi-tier cache adapter | New capability |
| ArchUnit tests | New tests |

---

## 5. Context Snapshot

### Technology Stack
- **Runtime**: JDK 25 LTS, Kotlin 2.4.10, Spring Boot 4.1.0
- **Build**: Gradle multi-module (Kotlin DSL)
- **Database**: PostgreSQL (Podman), Flyway migrations
- **Cache**: Redis (existing L2), Caffeine (planned L1)
- **Messaging**: Kafka (existing SSO events)
- **CQRS**: eventsourcing-utils (base-core, 0% adoption)

### Key Design Decisions (from brainstorm)
1. **Bridge Pattern**: AuthException extends BusinessException but keeps ProblemDetail + HTTP status
2. **Handler count**: 5 Commands + 4 Queries = 9 handlers
3. **Phase 1 safety**: Zero blast radius — only adds files
4. **Feature flag**: `app.cqrs.enabled` for Phase 2 rollout
5. **@Version opt-in**: VersionedAuditableEntity, NOT global

### Risk Registry
| Risk | Level | Mitigation |
|------|:-----:|------------|
| AuthException → BusinessException response format | 🔴 HIGH | Bridge pattern preserves ProblemDetail |
| CQRS handler proliferation | 🟢 LOW | 9 handlers is reasonable for scope |
| Phase 2 controller rewire | 🟡 MEDIUM | Feature flag rollout |
| N+1 fix in RbacEngine | 🟢 LOW | Single JOIN query replaces loop |
