# Architecture Debate Report

## Multi-Agent Review Panel
| Agent | Role |
|:---|:---|
| 🏗️ **Architect** | Propose architecture patterns |
| 😈 **Skeptic** | Attack weaknesses, find failure modes |
| 🔒 **Constraint Guardian** | Enforce non-functional constraints |
| 👤 **User Advocate** | End-user impact assessment |
| ⚖️ **Arbiter** | Resolve conflicts, final recommendation |

---

## Options Evaluated

### Option A: Modular Monolith (Spring Modulith)
- **Architect**: Leverages existing Spring Modulith dependency. Single deployment unit, module boundaries via package structure. Inter-module communication via events. DB shared but schema-separated.
- **Skeptic**: Module boundaries can leak. As RBAC+PBAC grows, single deployment becomes bottleneck. But for auth service specifically, this is unlikely — auth is naturally a single bounded context.
- **Constraint Guardian**: ✅ Small team (1 dev), fast deployment, single DB connection pool. Spring Modulith already in `build.gradle.kts`.
- **User Advocate**: ✅ Low latency (no network hops for internal calls). Permission checks are in-process.
- **Score**: 9/10

### Option B: Microservices (Separate User/Permission/Policy services)
- **Architect**: Maximum separation of concerns. User Service, Permission Service, Policy Engine as independent services.
- **Skeptic**: ❌ Massive overkill for auth. Network latency on every permission check. Distributed transaction for role changes. 3x infra cost. Auth is a single domain — splitting it creates artificial boundaries.
- **Constraint Guardian**: ❌ Team size = 1 developer. Cannot maintain 3 services. Operational overhead > benefit.
- **User Advocate**: ⚠️ Network latency on permission checks impacts every API call.
- **Score**: 3/10

### Option C: Hexagonal Architecture (Ports & Adapters) within Modular Monolith
- **Architect**: Modular Monolith structure, but internal modules follow Hexagonal/Clean Architecture. Domain core is pure Kotlin, no framework dependencies. Adapters for REST, JPA, JWT.
- **Skeptic**: ⚠️ More boilerplate (ports, adapters, mappers). But this aligns with base-core standards and enables future extraction to microservices if needed.
- **Constraint Guardian**: ✅ Aligns with existing base-core architecture. PersistentAuditableEntity, BaseEntity already follow this pattern.
- **User Advocate**: ✅ No user-facing impact. Better testability means fewer bugs.
- **Score**: 8/10

## Evaluation Matrix

| Criteria | Modular Monolith | Microservices | Hexagonal+Modular |
|:---|:---:|:---:|:---:|
| Team fit (1 dev) | ✅ | ❌ | ✅ |
| Performance | ✅ | ⚠️ | ✅ |
| Scalability | ⚠️ | ✅ | ⚠️ |
| Maintainability | ⚠️ | ✅ | ✅ |
| Deployment simplicity | ✅ | ❌ | ✅ |
| Base-core alignment | ✅ | ❌ | ✅ |
| Future extraction | ⚠️ | ✅ | ✅ |
| **Total Score** | **9/10** | **3/10** | **8/10** |

## Recommended Architecture

### ✅ **Option A: Modular Monolith** with elements of **Option C: Hexagonal Architecture**

**Rationale** (synthesized from all agents):
1. Auth RBAC+PBAC is a **single bounded context** — splitting into microservices creates artificial complexity
2. Spring Modulith already in dependencies — leverage it
3. Apply Hexagonal principles **within modules** (domain core pure, adapters for infra)
4. Single deployment = simpler ops for 1-dev team
5. Module boundaries enable future extraction if scale demands

### Architecture Structure
```
auth-service/
├── src/main/kotlin/com/ntt/authservice/
│   ├── AuthServiceApplication.kt
│   ├── shared/                     # Cross-cutting concerns
│   │   ├── config/
│   │   ├── exception/
│   │   └── security/
│   ├── auth/                       # Module: Authentication
│   │   ├── domain/                 # Pure domain (no framework)
│   │   │   ├── model/
│   │   │   └── service/
│   │   ├── application/            # Use cases
│   │   ├── adapter/
│   │   │   ├── in/web/             # REST controllers
│   │   │   └── out/persistence/    # JPA repositories
│   │   └── port/                   # Interfaces
│   ├── domain/                     # Module: Domain Management
│   │   ├── domain/
│   │   ├── application/
│   │   ├── adapter/
│   │   └── port/
│   ├── rbac/                       # Module: RBAC Engine
│   │   ├── domain/
│   │   ├── application/
│   │   ├── adapter/
│   │   └── port/
│   └── pbac/                       # Module: PBAC Policy Engine
│       ├── domain/
│       ├── application/
│       ├── adapter/
│       └── port/
```

## Trade-offs Accepted
1. **Monolith limitation**: Accept single deployment bottleneck, acceptable for auth workload
2. **Hexagonal overhead**: Accept extra boilerplate (ports/adapters) for testability and future extraction
3. **No Gateway integration (MVP)**: Accept direct service calls, defer thunx-style gateway integration

## Revisit Triggers
- When permission check volume > 10K/sec → consider Redis caching layer
- When team grows > 3 devs → consider extracting PBAC engine to separate service
- When multiple API Gateways needed → implement thunx-style query rewriting
