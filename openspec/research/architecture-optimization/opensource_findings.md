# Open Source Findings — Architecture Optimization

## Evaluation Context

Đây là feature về **architecture optimization** (refactoring sang Clean/Hexagonal/Onion Architecture), không phải feature cần tìm open source library mới. Phân tích tập trung vào **frameworks/libraries đã có trong base-core** và các alternatives có thể bổ sung.

---

## 1. CQRS / Event Sourcing Frameworks

### 1.1 **eventsourcing-utils (In-house)** — base-core component ✅ SELECTED

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 4 | Command/Query Bus, AggregateRoot, EventBus, Projection, Snapshot, Upcasters |
| Integration fit | 5 | Same Gradle build, same conventions, auto-discovered by `base-cqrs-starter` |
| Maintenance | 5 | In-house — full control |
| Documentation | 3 | Code-level docs only |
| Community | 1 | Internal only |
| **TOTAL** | **18/25** | |

**Gap Analysis**: 
- ✅ CommandBus/QueryBus — đủ cho Business Handler pattern
- ✅ EventBus — đủ cho domain event publishing
- ✅ Snapshot — có cho performance optimization
- ⚠️ Saga support — chưa có (không cần ở phase 1)
- ⚠️ Event Store persistence — cần implement adapter

### 1.2 **Axon Framework** — Alternative evaluated

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 5 | Full CQRS + Event Sourcing + Saga + DeadlineManager |
| Integration fit | 2 | Heavyweight, opinionated, locks into Axon ecosystem |
| Maintenance | 4 | AxonIQ backed, active development |
| Documentation | 5 | Extensive docs + training |
| Community | 4 | 4.8k GitHub stars |
| **TOTAL** | **20/25** | |

**Why NOT selected**: Quá heavyweight cho use case hiện tại. `eventsourcing-utils` đã cung cấp đủ CQRS primitives. Axon thêm complexity không cần thiết khi chỉ cần Command/Query dispatch pattern.

**Source**: [github.com/AxonFramework/AxonFramework](https://github.com/AxonFramework/AxonFramework)

### 1.3 **Marten** (for .NET, reference only)

Không applicable — JVM ecosystem. Liệt kê để reference pattern: Event Store + Document DB approach.

---

## 2. gRPC Framework

### 2.1 **spring-boot-starter-grpc** (Spring Boot 4.1 Native) ✅ SELECTED

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 5 | Native auto-configuration, client/server, health, reflection |
| Integration fit | 5 | First-party Spring Boot support |
| Maintenance | 5 | Spring team maintained |
| Documentation | 4 | Spring Boot reference docs |
| Community | 5 | Entire Spring ecosystem |
| **TOTAL** | **24/25** | |

**Why selected**: Native Spring Boot 4.1 support. Zero additional complexity. Auto-configures gRPC server/client with Spring conventions.

### 2.2 **grpc-spring-boot-starter** (LogNet) — Alternative

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 4 | Good feature set |
| Integration fit | 3 | Third-party, may conflict with native |
| Maintenance | 3 | Community maintained |
| Documentation | 3 | README-level |
| Community | 4 | 3.5k GitHub stars |
| **TOTAL** | **17/25** | |

**Why NOT selected**: Spring Boot 4.1 now has NATIVE gRPC support. Third-party starter is redundant.

**Source**: [github.com/LogNet/grpc-spring-boot-starter](https://github.com/LogNet/grpc-spring-boot-starter)

---

## 3. Object Mapping

### 3.1 **MapStruct 1.6.3** ✅ SELECTED (already in version catalog)

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 5 | Compile-time mapping, null handling, collections, nested |
| Integration fit | 5 | Already in `libs.versions.toml`, Kotlin support |
| Performance | 5 | Compile-time code generation — zero reflection overhead |
| Maintenance | 5 | Active development, major release 1.6 |
| **TOTAL** | **20/20** | |

**Source**: [mapstruct.org](https://mapstruct.org/)

### 3.2 **ModelMapper** — Alternative NOT selected

Reflection-based → performance overhead at scale. MapStruct generates code at compile time → zero overhead.

---

## 4. Caching — Multi-Tier

### 4.1 **Caffeine** — L1 In-Process Cache ✅ RECOMMENDED

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 5 | Async loading, bounded, eviction policies, statistics |
| Performance | 5 | Near-optimal hit rate (Window TinyLFU), <1μs access |
| Integration fit | 5 | Spring Cache abstraction native support |
| Maintenance | 5 | Ben Manes maintained, very active |
| **TOTAL** | **20/20** | |

**Gap**: Not currently in base-core starters. Needs to be added as dependency.

**Source**: [github.com/ben-manes/caffeine](https://github.com/ben-manes/caffeine)

### 4.2 **Spring Data Redis** — L2 Distributed Cache ✅ ALREADY USED

Already in auth-service. No evaluation needed.

---

## 5. Architecture Testing

### 5.1 **ArchUnit 1.4.2** ✅ SELECTED (already in version catalog)

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 5 | Package dependency rules, layer checks, naming conventions |
| Integration fit | 5 | Already in `libs.versions.toml`, JUnit 5 integration |
| Maintenance | 5 | Active development |
| **TOTAL** | **15/15** | |

**Source**: [archunit.org](https://www.archunit.org/)

---

## 6. HTTP Client

### 6.1 **@HttpExchange + RestClient** (Spring Boot 4.1 Native) ✅ SELECTED

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Feature completeness | 5 | Declarative interfaces, groups, auto-configuration |
| Integration fit | 5 | Native Spring Boot 4.1 |
| Performance | 5 | Connection pooling, timeouts, retry built-in |
| Maintenance | 5 | Spring team |
| **TOTAL** | **20/20** | |

**Why selected**: SOTA — `@ImportHttpServices` in SB 4.1 makes it trivial to configure. Type-safe, compile-time checked.

---

## Summary

| Category | Selected | Alternative Evaluated | Decision |
|----------|----------|----------------------|----------|
| CQRS | eventsourcing-utils (in-house) | Axon Framework | **Use existing** — sufficient for current needs |
| gRPC | spring-boot-starter-grpc (native) | LogNet grpc-spring-boot-starter | **Use native** — SB 4.1 built-in |
| Mapping | MapStruct 1.6.3 | ModelMapper | **Already in catalog** — compile-time perf |
| L1 Cache | Caffeine | Guava Cache | **Add new** — not in base-core yet |
| L2 Cache | Spring Data Redis | — | **Already in use** |
| Arch Testing | ArchUnit 1.4.2 | — | **Already in catalog** |
| HTTP Client | @HttpExchange (SB 4.1) | RestTemplate, WebClient | **Use native** — SOTA |
