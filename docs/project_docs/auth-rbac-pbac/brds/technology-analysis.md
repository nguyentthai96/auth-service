# Technology Stack Analysis

## Evaluation Matrix

| Layer | Option A | Option B | Option C | Chosen | Why |
|:---|:---|:---|:---|:---:|:---|
| **Language** | Kotlin | Java | — | Kotlin | Existing codebase, null-safety, data classes |
| **Framework** | Spring Boot 3.x | Quarkus | Micronaut | Spring Boot | Existing setup, team expertise, ecosystem |
| **Database** | PostgreSQL | MySQL | MongoDB | PostgreSQL | JSONB, RLS, confirmed by user |
| **ORM** | JPA/Hibernate | MyBatis | jOOQ | JPA | Base-core alignment, entity inheritance |
| **Migration** | Flyway | Liquibase | — | Flyway | Simpler, SQL-first, user requested SQL scripts |
| **Auth** | Custom JWT | Keycloak | Auth0 | Custom JWT | Full control, no external dependency |
| **API Format** | REST + JSON | GraphQL | gRPC | REST | Simplicity, existing patterns |
| **Serialization** | Jackson | kotlinx.serialization | Gson | Jackson | Spring default, already in deps |
| **Testing** | JUnit 5 + MockitoBean | Kotest | — | JUnit 5 | Base-core standards |
| **Container** | Docker Compose | k8s (Helm) | — | Docker Compose | Dev environment, user confirmed |

---

## Per-Layer Debate Summary

### Language: Kotlin
- **Architect**: Kotlin — null-safety, data classes perfect for DTOs, coroutines for async
- **Skeptic**: Kotlin is already the project language. No debate needed.
- **Decision**: Kotlin ✅ (unanimous)

### Framework: Spring Boot 3.x
- **Architect**: Spring Boot — Security, JPA, Modulith, Actuator all built-in. OAuth2 Authorization Server already in deps.
- **Skeptic**: Spring Boot memory footprint is high (~300MB). But auth service is long-running, not serverless. Acceptable.
- **Pragmatist**: Team knows Spring Boot. `build.gradle.kts` already configured.
- **Decision**: Spring Boot ✅ (3/3 consensus)

### Database: PostgreSQL
- **Architect**: PostgreSQL — JSONB for policy conditions, RLS for tenant isolation, UUID native support, mature ecosystem
- **Skeptic**: No concerns. User confirmed PostgreSQL. Already in runtime dependencies.
- **Guardian**: License: PostgreSQL License (permissive). Security track record: excellent.
- **Decision**: PostgreSQL ✅ (unanimous, user-confirmed)

### ORM: JPA/Hibernate
- **Architect**: JPA — entity inheritance from base-core (`PersistentAuditableEntity`), Spring Data repositories, QueryDSL for dynamic queries
- **Skeptic**: ⚠️ Hibernate N+1 risk with deep entity graphs (User → Groups → Roles → Permissions). Mitigate with: `@EntityGraph`, `JOIN FETCH`, and strategic `@BatchSize`.
- **Pragmatist**: MyBatis also in deps but JPA better for domain modeling. Keep MyBatis for complex reporting queries if needed.
- **Decision**: JPA ✅ with N+1 awareness

### Migration: Flyway
- **Architect**: Flyway — SQL-first migrations, versioned, repeatable, callbacks
- **Skeptic**: User explicitly requested SQL scripts. Flyway is the right choice.
- **Decision**: Flyway ✅ (user-requested)
- **Revisit-if**: Never — Flyway is the standard

### Auth: Custom JWT
- **Architect**: Custom JWT engine — full control, no external OPA/Casbin/Keycloak. Inspired by thunx 2-stage architecture (early RBAC check, postponed PBAC evaluation).
- **Skeptic**: ⚠️ Risk of security bugs in custom implementation. Mitigate: Use battle-tested libraries (jjwt) for token signing, focus custom code only on permission evaluation logic.
- **Guardian**: RS256 signing (asymmetric). Token validation is a solved problem with `spring-security-oauth2-resource-server`.
- **Decision**: Custom ✅ — use `jjwt` for token ops, custom code for permission evaluation

## Thunx-Inspired Patterns to Adopt

| Concept from Thunx | Our Adaptation |
|:---|:---|
| Authorization Predicate (thunk) | `PermissionPredicate` — encapsulates RBAC+PBAC evaluation result |
| 2-Stage decision | Stage 1: JWT filter (role check) → Stage 2: Service layer (policy eval) |
| Query rewriting | Post-MVP: JPA Specification rewriting based on predicate |
| PDP (Policy Decision Point) | `PolicyEvaluator` service — evaluates JSONB conditions |
| PEP (Policy Enforcement Point) | `PermissionFilter` — Spring Security filter |

## Competitor Technology Insights

| Competitor | Stack | Lesson |
|:---|:---|:---|
| **Keycloak** | Java, WildFly, PostgreSQL | Realm concept ≈ our Domain concept |
| **Casbin** | Go/Java, Model-Policy separation | Adapter pattern for different backends |
| **SpiceDB** | Go, Zanzibar model | Relationship tuples — overkill for us but good for future |
| **Thunx** | Spring Boot, OPA, Gateway | 2-stage auth, query rewriting — adopt pattern not tool |
