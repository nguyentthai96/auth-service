---
type: brainstorm_notes
change: solid-architecture-refactoring
date: 2026-09-22
selected_direction: "BUILD Lightweight — Reuse eventsourcing-utils + Immutable Pipeline"
pre_flow: "Command"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: SOLID Architecture Refactoring

## Date
2026-09-22

## Context

Refactoring kiến trúc `auth-service` để giải quyết SOLID violations đã xác định qua `/wf_feature_research` và `/wf_pre_openspec`. User yêu cầu code "chuẩn nhất, clean nhất". Research đã chốt BUILD approach — tự implement lightweight patterns.

## Questions Asked & Answers

### Q1 (OQ-001): CommandHandler<C,R> từ eventsourcing-utils có sẵn commandType()?
**→ A: CÓ — đã verified tại codebase.**

```kotlin
// eventsourcing-utils đã có sẵn:
import com.ntt.eventsourcingutils.lib.cqrs.command.Command       // interface Command<R>
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler // interface CommandHandler<C,R>
import com.ntt.eventsourcingutils.lib.cqrs.query.Query            // interface Query<R>
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryHandler     // interface QueryHandler<Q,R>
```

**Fact**: `commandType(): Class<C>` đã có sẵn — LoginHandler override nó:
```kotlin
override fun commandType(): Class<LoginCommand> = LoginCommand::class.java
```

**User decision**: Reuse eventsourcing-utils interfaces. Viết `CommandBus`/`QueryBus` dùng lại chúng, thiết kế thành **cụm reusable** cho các service khác.

### Q2 (OQ-002): AuthenticationContext — mutable hay immutable?
**→ A: IMMUTABLE (data class)**

Trade-off đã xem xét:
- Immutable: An toàn hơn (no side effects), dễ debug, testable
- Mutable: Performance tốt hơn (không copy), ít boilerplate
- **Chọn: Immutable** → Pipeline return new context per step, không mutate

---

## Approaches Considered

### Approach 1: CommandBus trong eventsourcing-utils (library-level)

```
┌──────────────────────────────────┐
│ eventsourcing-utils (library)    │
│ ┌──────────┐  ┌───────────┐     │
│ │ Command  │  │ Query     │     │
│ │ Handler  │  │ Handler   │     │
│ │          │  │           │     │
│ ├──────────┤  ├───────────┤     │
│ │CommandBus│  │ QueryBus  │ NEW │
│ │          │  │           │     │
│ └──────────┘  └───────────┘     │
└──────────────────────────────────┘
         ↑                ↑
   auth-service    system-admin-service
   account-service notification-service
```

- **Pros**: Reusable across ALL services, single source of truth, upstream fix benefits everyone
- **Cons**: Cần publish new version eventsourcing-utils, longer deployment cycle
- **Verdict**: ⭐ **IDEAL** — nhưng phụ thuộc vào quyền modify eventsourcing-utils

### Approach 2: CommandBus trong auth-service (service-level)

```
┌──────────────────────────────────────────┐
│ auth-service                             │
│ ┌──────────────────────┐                 │
│ │ shared/cqrs/         │                 │
│ │ ├─ CommandBus.kt     │ ← interface     │
│ │ ├─ SpringCommandBus  │ ← Spring impl   │
│ │ ├─ QueryBus.kt       │ ← interface     │
│ │ └─ SpringQueryBus    │ ← Spring impl   │
│ └──────────────────────┘                 │
│         ↓ uses                           │
│ eventsourcing-utils                      │
│ (Command, CommandHandler, Query, QH)     │
└──────────────────────────────────────────┘
```

- **Pros**: Nhanh, không cần lib release, scoped
- **Cons**: Duplicate nếu service khác cần
- **Verdict**: **PRAGMATIC** — ship now, extract to lib later

### Approach 3: Hybrid — Service-level NOW, extract to lib LATER

- Implement `CommandBus`/`QueryBus` trong `auth-service/shared/cqrs/`
- Design interfaces reusable (no auth-service specific code)
- Khi stable → copy vào `eventsourcing-utils` → xóa bản local
- **Verdict**: ⭐⭐ **SELECTED** — best of both worlds

---

## Selected Direction: Hybrid (Approach 3)

**Reasoning**:
1. User muốn reuse eventsourcing-utils base → giữ `Command<R>`, `CommandHandler<C,R>` từ lib
2. `CommandBus`/`QueryBus` chưa có trong lib → viết local, designed for extraction
3. `AuthenticationContext` = immutable `data class` → pipeline copy per step
4. Pipeline steps return `Pair<StepResult, AuthenticationContext>` — new context per step

### Design Decision: Immutable Pipeline Flow

```
LoginCommand
     │
     ▼
┌─────────────────────────┐
│ AuthenticationPipeline   │
│                         │
│  ctx₀ = empty context   │
│     │                   │
│     ▼                   │
│  ┌─────────────────┐   │
│  │SecurityPreCheck  │   │   ctx₁ = step.execute(ctx₀)
│  │Step              │──►│   return (Continue, ctx₁)
│  └─────────────────┘   │
│     │                   │
│     ▼                   │
│  ┌─────────────────┐   │
│  │CredentialVerify  │   │   ctx₂ = step.execute(ctx₁)
│  │Step              │──►│   return (Continue, ctx₂)
│  └─────────────────┘   │        or
│     │                   │   return (ShortCircuit(MfaRequired), ctx₂)
│     ▼                   │
│  ┌─────────────────┐   │
│  │PolicyEnforcement │   │   ctx₃ = step.execute(ctx₂)
│  │Step              │──►│
│  └─────────────────┘   │
│     │                   │
│     ▼                   │
│  ┌─────────────────┐   │
│  │SessionEstablish  │   │   ctx₄ = step.execute(ctx₃)
│  │Step              │──►│
│  └─────────────────┘   │
│     │                   │
│     ▼                   │
│  ┌─────────────────┐   │
│  │TokenIssuance     │   │   ctx₅ = step.execute(ctx₄)
│  │Step              │──►│   ctx₅.authToken is set
│  └─────────────────┘   │
│     │                   │
│     ▼                   │
│  LoginResult.Success    │
└─────────────────────────┘
```

### Design Decision: CommandBus Interface (reuse-ready)

```kotlin
// Interface — reusable, no auth-specific code
interface CommandBus {
    fun <R> dispatch(command: Command<R>): R
}

// Spring implementation — uses existing CommandHandler<C,R>.commandType()
@Component
class SpringCommandBus(
    handlers: List<CommandHandler<*, *>>
) : CommandBus {
    
    // Build type → handler map using existing commandType() method
    private val handlerMap: Map<Class<*>, CommandHandler<*, *>> = 
        handlers.associateBy { it.commandType() }
    
    @Suppress("UNCHECKED_CAST")
    override fun <R> dispatch(command: Command<R>): R {
        val handler = handlerMap[command::class.java] as? CommandHandler<Command<R>, R>
            ?: throw IllegalArgumentException("No handler for ${command::class.simpleName}")
        return handler.handle(command)
    }
}
```

**Key insight**: `commandType()` đã có sẵn → `SpringCommandBus` chỉ cần `associateBy { it.commandType() }`. Không cần extend hay modify eventsourcing-utils.

### Design Decision: Immutable AuthenticationContext

```kotlin
data class AuthenticationContext(
    val command: LoginCommand,
    val user: User? = null,
    val domainCode: String? = null,
    val domain: DomainInfo? = null,
    val roles: List<RoleInfo> = emptyList(),
    val loginSession: LoginSessionInfo? = null,
    val promotionResult: PromotionResult? = null,
    val authToken: AuthToken? = null
) {
    // Convenience: copy with user
    fun withUser(user: User) = copy(user = user)
    fun withDomain(domain: DomainInfo) = copy(domain = domain)
    fun withRoles(roles: List<RoleInfo>) = copy(roles = roles)
    fun withSession(session: LoginSessionInfo) = copy(loginSession = session)
    fun withPromotion(result: PromotionResult) = copy(promotionResult = result)
    fun withAuthToken(token: AuthToken) = copy(authToken = token)
}

// Step interface — returns new context
interface AuthenticationStep {
    val order: Int
    fun execute(context: AuthenticationContext): StepOutcome
}

sealed class StepOutcome {
    data class Continue(val context: AuthenticationContext) : StepOutcome()
    data class ShortCircuit(val result: LoginResult) : StepOutcome()
}
```

**Trade-off analysis (immutable)**:
- ✅ No side effects — mỗi step nhận ctx input, return ctx output
- ✅ Easy to test — assert trên returned context
- ✅ Easy to debug — log ctx diff giữa input/output
- ⚠️ Kotlin data class copy() shallow — OK vì inner objects là immutable (String, List, etc.)
- ⚠️ ~5 copy operations per login — negligible vs I/O (DB, Redis, JWT signing)

---

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE (refactoring existing logic)
- Flow type: Command (no user-facing transaction)
- Affected modules: `auth`, `rbac`, `pbac`, `shared`

## GitNexus Findings (if explored)
N/A — research phase đã scan codebase đầy đủ

## 🔴 CRITICAL FINDING (Post-brainstorm Discovery)

**`eventsourcing-utils` ĐÃ CÓ SẴN CommandBus, QueryBus, SpringCommandBus, SpringQueryBus!**

Decompiled from JAR (`eventsourcing-utils-0.0.1-SNAPSHOT.jar`):

```
com/ntt/eventsourcingutils/lib/cqrs/command/CommandBus.class     → interface { dispatch(Command<R>): R }
com/ntt/eventsourcingutils/lib/cqrs/SpringCommandBus.class       → SpringCommandBus(List<CommandHandler>) — auto-wires
com/ntt/eventsourcingutils/lib/cqrs/query/QueryBus.class         → interface { dispatch(Query<R>): R }
com/ntt/eventsourcingutils/lib/cqrs/SpringQueryBus.class         → SpringQueryBus(List<QueryHandler>) — auto-wires
```

**Impact trên implementation plan**:
- FR-009 **SIMPLIFIED** — Không cần tạo CommandBus/QueryBus. Chỉ cần **inject bean** từ lib
- FR-010 **SIMPLIFIED** — CqrsAuthController inject `CommandBus` + `QueryBus` trực tiếp
- Cần verify: `SpringCommandBus` auto-configure hay cần `@Bean` declaration?
- Cần verify: `SpringCommandBus` có logging/metrics decorator hay cần tự thêm?

**Kết quả**: Phase P2a (CommandBus infrastructure) từ **4-6h → 1-2h** (chỉ wire beans + tests)

## Open Questions for Design Phase

- [RESOLVED] OQ-001: `commandType()` đã có sẵn trong `CommandHandler` → reuse trực tiếp
- [RESOLVED] OQ-002: Immutable `data class` cho `AuthenticationContext`
- [RESOLVED] OQ-003: `QueryBus` cần thiết ngay không? → **CÓ SẴN** trong lib, inject thẳng
- [OPEN] OQ-004: `SpringCommandBus` có `@Component` auto-configure hay cần `@Bean`?
- [OPEN] OQ-005: Cần logging decorator cho CommandBus không?

## Open Questions for URD Analysis
N/A — URD đã phân tích trong `/wf_pre_openspec`
