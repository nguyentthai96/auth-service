# Design: SOLID Architecture Refactoring

> **Change**: solid-architecture-refactoring
> **Type**: MAINTENANCE
> **Flow**: Command
> **Profile**: auth-service | Clean Architecture + CQRS | Kotlin

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    BEFORE                                │
│                                                         │
│  CqrsAuthController (13 deps)                           │
│     ├─ loginHandler (17 deps — GOD CLASS)               │
│     ├─ registerHandler                                  │
│     ├─ refreshTokenHandler                              │
│     ├─ switchDomainHandler                              │
│     ├─ revokeSessionsHandler                            │
│     ├─ buildAuthResponseHandler                         │
│     ├─ passwordPolicyService                            │
│     ├─ loginSessionService                              │
│     ├─ domainLookupService                              │
│     ├─ securityProperties                               │
│     ├─ jwtService                                       │
│     ├─ notificationGateway                              │
│     └─ userRepository ← DIP VIOLATION                   │
│                                                         │
│  PolicyEvaluator (when clause — OCP VIOLATION)          │
│  MfaService (when clause — OCP VIOLATION)               │
│  RbacControllers (357 LOC — SRP VIOLATION, DIP)         │
└─────────────────────────────────────────────────────────┘

                    ▼ REFACTORING ▼

┌─────────────────────────────────────────────────────────┐
│                     AFTER                                │
│                                                         │
│  CqrsAuthController (3 deps)                            │
│     ├─ commandBus: CommandBus ← from eventsourcing-utils│
│     ├─ queryBus: QueryBus ← from eventsourcing-utils    │
│     └─ securityProperties                               │
│                                                         │
│  LoginHandler (3 deps — delegates to Pipeline)          │
│     ├─ pipeline: AuthenticationPipeline                  │
│     ├─ loginEventRecorder                               │
│     └─ commandType() → existing interface               │
│                                                         │
│  AuthenticationPipeline (ordered steps)                  │
│     ├─ SecurityPreCheckStep                             │
│     ├─ CredentialVerificationStep                       │
│     ├─ PolicyEnforcementStep                            │
│     ├─ SessionEstablishmentStep                         │
│     └─ TokenIssuanceStep                                │
│                                                         │
│  MfaProviderRegistry → dispatches MfaProvider impls     │
│  PolicyEvaluator → ConditionOperator enum               │
│  RBAC/PBAC → Application Use Cases + Port interfaces    │
└─────────────────────────────────────────────────────────┘
```

## 2. Component Design

### 2.1 CommandBus / QueryBus (REUSE — eventsourcing-utils)

```
eventsourcing-utils-0.0.1-SNAPSHOT.jar ĐÃ CÓ SẴN:
├── command/
│   ├── Command<R>         — interface, generic result
│   ├── CommandHandler<C,R> — interface, has commandType(): Class<C>
│   └── CommandBus          — interface { dispatch(Command<R>): R }
├── query/
│   ├── Query<R>           — interface
│   ├── QueryHandler<Q,R>  — interface
│   └── QueryBus           — interface { dispatch(Query<R>): R }
├── SpringCommandBus       — KHÔNG có @Component
└── SpringQueryBus         — KHÔNG có @Component
```

**Action**: Tạo `@Configuration` bean declaration trong `shared/cqrs/`:

```kotlin
// shared/cqrs/CqrsConfig.kt
@Configuration
class CqrsConfig {

    @Bean
    fun commandBus(handlers: List<CommandHandler<*, *>>): CommandBus =
        LoggingCommandBus(SpringCommandBus(handlers))

    @Bean
    fun queryBus(handlers: List<QueryHandler<*, *>>): QueryBus =
        LoggingQueryBus(SpringQueryBus(handlers))
}
```

### 2.2 Logging Decorator (OQ-005 resolved)

```kotlin
// shared/cqrs/LoggingCommandBus.kt
class LoggingCommandBus(private val delegate: CommandBus) : CommandBus {
    private val log = LoggerFactory.getLogger(LoggingCommandBus::class.java)

    override fun <R> dispatch(command: Command<R>): R {
        val commandName = command::class.simpleName
        log.info(">>> Dispatching command: {}", commandName)
        val start = System.nanoTime()
        return try {
            val result = delegate.dispatch(command)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            log.info("<<< Command {} completed in {}ms", commandName, elapsed)
            result
        } catch (e: Exception) {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            log.error("<<< Command {} FAILED after {}ms: {}", commandName, elapsed, e.message)
            throw e
        }
    }
}
```

### 2.3 Authentication Pipeline (Chain of Responsibility)

```
                  Immutable Flow
                  ─────────────

LoginCommand ──► AuthenticationContext(command=cmd)
                     │
      ┌──────────────┼──────────────┐
      ▼              │              │
 Step 1: Security    │              │
 PreCheck            │              │
      │              │              │
      ▼ Continue(ctx₁)             │
 Step 2: Credential  │              │
 Verification        │              │
      │              │              │
      ▼ Continue(ctx₂)             │
 Step 3: Policy      │        ShortCircuit
 Enforcement    ─────┼──────► MfaRequired
      │              │
      ▼ Continue(ctx₃)
 Step 4: Session
 Establishment
      │
      ▼ Continue(ctx₄)
 Step 5: Token
 Issuance
      │
      ▼ Continue(ctx₅)
 LoginResult.Success(ctx₅.authToken)
```

**Key interfaces**:
```kotlin
// auth/application/pipeline/AuthenticationStep.kt
interface AuthenticationStep {
    val order: Int
    val name: String
    fun execute(context: AuthenticationContext): StepOutcome
}

// auth/application/pipeline/StepOutcome.kt
sealed class StepOutcome {
    data class Continue(val context: AuthenticationContext) : StepOutcome()
    data class ShortCircuit(val result: LoginResult) : StepOutcome()
}

// auth/application/pipeline/AuthenticationContext.kt — IMMUTABLE
data class AuthenticationContext(
    val command: LoginCommand,
    val user: User? = null,
    val domainCode: String? = null,
    val domain: DomainInfo? = null,
    val roles: List<RoleInfo> = emptyList(),
    val loginSession: LoginSessionInfo? = null,
    val promotionResult: PromotionResult? = null,
    val authToken: AuthToken? = null
)
```

### 2.4 MFA Provider Strategy

```
MfaService.initiate(method)  ──►  MfaProviderRegistry.get(method)
                                       │
                              ┌────────┼────────┐
                              ▼        ▼        ▼
                           SmsMfa  EmailMfa  TotpMfa
                           Provider Provider  Provider
```

### 2.5 ConditionOperator Enum (PolicyEvaluator OCP fix)

```kotlin
// pbac/application/ConditionOperator.kt
enum class ConditionOperator(val evaluate: (actual: Any?, expected: Any?) -> Boolean) {
    EQ({ a, b -> a?.toString() == b?.toString() }),
    NEQ({ a, b -> a?.toString() != b?.toString() }),
    IN({ a, b -> a?.toString() in ((b as? List<*>)?.map { it.toString() } ?: emptyList()) }),
    NOT_IN({ a, b -> a?.toString() !in ((b as? List<*>)?.map { it.toString() } ?: emptyList()) }),
    GT({ a, b -> compareNumbers(a, b) > 0 }),
    GTE({ a, b -> compareNumbers(a, b) >= 0 }),
    LT({ a, b -> compareNumbers(a, b) < 0 }),
    LTE({ a, b -> compareNumbers(a, b) <= 0 }),
    CONTAINS({ a, b -> a?.toString()?.contains(b?.toString() ?: "") == true }),
    STARTS_WITH({ a, b -> a?.toString()?.startsWith(b?.toString() ?: "") == true });
    
    companion object {
        fun fromString(op: String): ConditionOperator =
            entries.find { it.name.equals(op, ignoreCase = true) }
                ?: throw PolicyEvaluationException("Unknown operator: $op")
    }
}
```

### 2.6 RBAC/PBAC Application Layer

```
  Controller Layer          Application Layer         Infrastructure Layer
  ──────────────           ─────────────────         ────────────────────
  RbacControllers     ──►  RoleManagementUseCase  ──► RolePort (interface)
  RolePermissionCtrl  ──►  PermissionUseCase      ──► PermissionPort        
  PolicyController    ──►  PolicyManagementUseCase──► PolicyPort             
                                                       │
                                                       ▼
                                                  JPA Repository (impl)
```

---

## 3. File Mapping

### New Files

| File | Package | Purpose |
|------|---------|---------|
| `CqrsConfig.kt` | `shared/cqrs` | `@Bean` for CommandBus, QueryBus with Logging decorator |
| `LoggingCommandBus.kt` | `shared/cqrs` | Decorator — logging + timing for commands |
| `LoggingQueryBus.kt` | `shared/cqrs` | Decorator — logging + timing for queries |
| `AuthenticationStep.kt` | `auth/application/pipeline` | Step interface |
| `StepOutcome.kt` | `auth/application/pipeline` | Sealed class (Continue/ShortCircuit) |
| `AuthenticationContext.kt` | `auth/application/pipeline` | Immutable data class |
| `AuthenticationPipeline.kt` | `auth/application/pipeline` | Pipeline orchestrator |
| `SecurityPreCheckStep.kt` | `auth/application/pipeline` | Step 1: lockout + captcha + rate limit |
| `CredentialVerificationStep.kt` | `auth/application/pipeline` | Step 2: password verify + upgrade |
| `PolicyEnforcementStep.kt` | `auth/application/pipeline` | Step 3: password expiry + MFA + session policy |
| `SessionEstablishmentStep.kt` | `auth/application/pipeline` | Step 4: session + device + promotion |
| `TokenIssuanceStep.kt` | `auth/application/pipeline` | Step 5: JWT generation |
| `MfaProvider.kt` | `auth/application/mfa` | Strategy interface |
| `MfaProviderRegistry.kt` | `auth/application/mfa` | Registry + dispatch |
| `SmsMfaProvider.kt` | `auth/application/mfa` | SMS implementation |
| `EmailMfaProvider.kt` | `auth/application/mfa` | Email implementation |
| `TotpMfaProvider.kt` | `auth/application/mfa` | TOTP implementation |
| `ConditionOperator.kt` | `pbac/application` | Enum operator strategy |
| `RoleManagementUseCase.kt` | `rbac/application` | RBAC business logic |
| `PermissionUseCase.kt` | `rbac/application` | Permission management |
| `PolicyManagementUseCase.kt` | `pbac/application` | PBAC management |
| `RolePort.kt` | `rbac/application/port` | Interface for role repository |
| `PermissionPort.kt` | `rbac/application/port` | Interface for permission repo |
| `PolicyPort.kt` | `pbac/application/port` | Interface for policy repo |
| `LockoutPort.kt` | `auth/application/port/out` | Interface for lockout adapter |

### Modified Files

| File | Changes |
|------|---------|
| `CqrsAuthController.kt` | Reduce 13 → 3 deps (CommandBus + QueryBus + SecurityProperties) |
| `LoginHandler.kt` | 17 → 3 deps (Pipeline + EventRecorder + commandType) |
| `PolicyEvaluator.kt` | Replace `when` clause with `ConditionOperator.fromString()` |
| `MfaService.kt` | Delegate to `MfaProviderRegistry` |
| `RbacControllers.kt` | Inject Use Cases instead of Repositories |
| `RolePermissionController.kt` | Inject Use Cases instead of Repositories |
| `PolicyController.kt` | Inject Use Cases instead of Repositories |
| `AccountLockoutService.kt` | Use `LockoutPort` instead of `RedisLockoutAdapter` |

---

## 4. Design Decisions

| ID | Decision | Rationale | Trade-off |
|----|----------|-----------|-----------|
| DD-001 | Reuse eventsourcing-utils CommandBus/QueryBus | Đã có sẵn, tested, Spring-compatible | Phụ thuộc lib version |
| DD-002 | `@Bean` declaration thay vì `@Component` | SpringCommandBus không có @Component annotation | Cần CqrsConfig class |
| DD-003 | Logging decorator pattern | User yêu cầu quản lý luồng truy cập | Thêm 1 layer indirection nhưng transparent |
| DD-004 | Immutable AuthenticationContext | User chọn immutable, safer cho pipeline | ~5 copy operations per login (negligible) |
| DD-005 | Single-pass policy evaluation | Avoid iterating policies twice (DENY + ALLOW) | Slightly more complex logic |
| DD-006 | ConditionOperator enum | OCP: thêm operator = thêm enum entry | Tất cả operators phải implement cùng signature |
