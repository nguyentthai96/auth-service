# Kết quả tìm kiếm Open Source: User Login Event

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Login Event (domain event for login success/failure with enriched context) |
| **Ngày tìm kiếm** | 2025-08-22 |
| **Số dự án tìm thấy** | 6 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot 3.x / PostgreSQL / Kafka / Event Sourcing |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"login event event-sourcing open source Java Kotlin"` | 4 relevant projects | Keycloak, Spring Authorization Server, Axon Framework, ORY Kratos |
| 2 | `"authentication audit event schema CloudEvents"` | 3 relevant results | CloudEvents spec, OCSF schema, CADF standard |
| 3 | `"identity provider login event model domain event"` | 2 relevant projects | Keycloak Events SPI, FusionAuth |
| 4 | `"user login event Kafka spring boot domain driven design"` | 2 relevant results | Axon Framework examples, Eventuate Tram |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Keycloak | https://github.com/keycloak/keycloak | 25k+ | Active (daily) | Apache-2.0 | ✅ Có |
| 2 | Spring Authorization Server | https://github.com/spring-projects/spring-authorization-server | 5k+ | Active (weekly) | Apache-2.0 | ✅ Có |
| 3 | Axon Framework | https://github.com/AxonFramework/AxonFramework | 5k+ | Active (weekly) | Apache-2.0 | ✅ Có |
| 4 | ORY Kratos | https://github.com/ory/kratos | 11k+ | Active (weekly) | Apache-2.0 | ✅ Có |
| 5 | FusionAuth | https://github.com/FusionAuth/fusionauth-java-client | 500+ | Active | Apache-2.0 | ❌ Không (commercial core, limited OSS event model) |
| 6 | Eventuate Tram | https://github.com/eventuate-tram/eventuate-tram-core | 1k+ | Monthly | Apache-2.0 | ❌ Không (generic event framework, not auth-specific) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core login event fields | Has basics (userId, timestamp) | Full enriched payload (device, geo, MFA, session) |
| **Applicability** (phù hợp tech stack) | 15% | Different language/framework | Java but different patterns | Kotlin/Spring Boot compatible, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + event schema reference |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Rich event model: LOGIN, LOGIN_ERROR, REGISTER, CODE_TO_TOKEN with full context (IP, client, session, realm, error) |
| Applicability | 5 | 15% | 0.75 | Java-based, custom SPI architecture — patterns transferable but code not directly reusable |
| Activity | 10 | 15% | 1.50 | Very active — daily commits, Red Hat backed |
| Documentation | 8 | 15% | 1.20 | Extensive docs on Events SPI, admin events, user events with EventType enum |
| Code quality | 8 | 15% | 1.20 | Well-tested, modular SPI design, clear separation of event types |
| Community | 10 | 10% | 1.00 | 25k+ stars, massive community |
| Popularity | 10 | 10% | 1.00 | Industry standard IDP |
| **Tổng điểm** | | | **8.45/10** | |

**Keycloak Event Model Details:**
- `EventType` enum: `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `REGISTER`, `CODE_TO_TOKEN`, `REFRESH_TOKEN`, etc.
- Event fields: `time`, `type`, `realmId`, `clientId`, `userId`, `sessionId`, `ipAddress`, `error`, `details` (Map)
- Login error details: `username`, `grant_type`, `client_id`, `auth_method`, `reason`
- Events persisted to `event_entity` table + publishable via SPI listeners
- Separate `AdminEvent` type for admin actions vs user events

#### Spring Authorization Server

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 4 | 20% | 0.80 | Minimal event model — relies on Spring Security events (AuthenticationSuccessEvent, AuthenticationFailureBadCredentialsEvent) |
| Applicability | 9 | 15% | 1.35 | Native Spring Boot — directly compatible tech stack |
| Activity | 8 | 15% | 1.20 | Active Spring project with regular releases |
| Documentation | 7 | 15% | 1.05 | Good Spring docs but event model not extensively documented |
| Code quality | 9 | 15% | 1.35 | Spring quality standards, well-tested |
| Community | 7 | 10% | 0.70 | 5k+ stars, growing |
| Popularity | 8 | 10% | 0.80 | Mainstream for Spring-based auth servers |
| **Tổng điểm** | | | **7.25/10** | |

**Spring Security Event Model Details:**
- `AuthenticationSuccessEvent` — wraps `Authentication` object
- `AuthenticationFailureBadCredentialsEvent`, `AuthenticationFailureLockedEvent`, etc. — failure subtypes
- `AbstractAuthenticationEvent` — base class with timestamp + authentication
- Uses Spring `ApplicationEventPublisher` — in-process only, no persistence
- No built-in event store or Kafka publishing — must add custom listeners

#### Axon Framework

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Full CQRS/ES framework — events are first-class citizens; auth events are user-defined |
| Applicability | 7 | 15% | 1.05 | Java/Kotlin compatible, Spring Boot integration; BUT requires Axon infrastructure (Axon Server or JPA event store) |
| Activity | 8 | 15% | 1.20 | Active development, regular releases |
| Documentation | 8 | 15% | 1.20 | Excellent docs on event sourcing patterns, event design, event upcasting |
| Code quality | 9 | 15% | 1.35 | Mature, well-tested framework |
| Community | 7 | 10% | 0.70 | 5k+ stars, active Discord community |
| Popularity | 7 | 10% | 0.70 | Popular in Java ES/CQRS space |
| **Tổng điểm** | | | **7.60/10** | |

**Axon Event Sourcing Patterns (Reference):**
- Events are POJOs annotated with `@DomainEvent`
- Event Store is a core abstraction — append-only, ordered by aggregate + sequence
- Aggregates apply events via `@EventSourcingHandler`
- Event upcasting for schema evolution
- Tracking event processors for projections + Kafka integration
- Pattern: Separate success/failure events vs. single event with status discriminator

#### ORY Kratos

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Webhook-based event system — `session.issued`, `session.revoked`, `identity.created` — rich payload |
| Applicability | 3 | 15% | 0.45 | Go-based — architecture patterns useful but code not transferable |
| Activity | 9 | 15% | 1.35 | Very active, ORY company-backed |
| Documentation | 8 | 15% | 1.20 | Good docs on webhook events, session model, identity schema |
| Code quality | 8 | 15% | 1.20 | Well-structured Go code, comprehensive tests |
| Community | 9 | 10% | 0.90 | 11k+ stars, active community |
| Popularity | 8 | 10% | 0.80 | Growing rapidly in cloud-native IDP space |
| **Tổng điểm** | | | **7.30/10** | |

**ORY Kratos Session Event Model:**
- Session events: `session.issued`, `session.extended`, `session.revoked`, `session.token_issued`
- Identity events: `identity.created`, `identity.updated`, `identity.deleted`
- Webhook payload includes: `flow` context, `identity` details, `session` metadata, `device` info
- Events delivered via webhooks — no built-in event store or Kafka

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Keycloak | 9 | 5 | 10 | 8 | 8 | 10 | 10 | **8.45** |
| 2 | Axon Framework | 7 | 7 | 8 | 8 | 9 | 7 | 7 | **7.60** |
| 3 | ORY Kratos | 7 | 3 | 9 | 8 | 8 | 9 | 8 | **7.30** |
| 4 | Spring Auth Server | 4 | 9 | 8 | 7 | 9 | 7 | 8 | **7.25** |

---

## 4. Gap Analysis chi tiết

### Keycloak — Gap Analysis

**Overall Score**: 8.45 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Login success event | ✅ | | Rich EventType.LOGIN with details map | - |
| Login failure event | ✅ | | EventType.LOGIN_ERROR with error reason | - |
| Device/IP context | ✅ | | IP address, session ID in event | No device fingerprint or user-agent parsing |
| Event persistence | ✅ | | Persisted to event_entity table | Custom table, not event-sourcing style |
| Kafka publishing | | ❌ | - | No built-in Kafka — requires custom SPI listener |
| CloudEvents envelope | | ❌ | - | Custom format, not CloudEvents |
| Transactional outbox | | ❌ | - | Direct persistence, no outbox pattern |
| Event schema versioning | | ❌ | - | No schema version field |
| Correlation ID | | ❌ | - | No correlation ID across services |
| MFA status in event | ⚠️ | | Partial — separate LOGIN event after MFA | Not in single event payload |
| Session promotion context | | ❌ | - | No anonymous session concept |

**Verdict**: Tham khảo event type enum + field design
**Recommendation**: Tham khảo pattern
**Reasoning**: Keycloak's `EventType` enum and `Event` class provide an excellent reference for login event field design. However, the code is Java with Keycloak-specific SPI architecture — cannot be used directly. Our system already has superior event infrastructure (CloudEvents envelope, transactional outbox, Kafka relay).

### Axon Framework — Gap Analysis

**Overall Score**: 7.60 / 10
**URL**: https://github.com/AxonFramework/AxonFramework

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Event sourcing infrastructure | ✅ | | Full ES framework with event store, upcasting, snapshots | Heavy dependency — our EventService is lighter |
| Event design patterns | ✅ | | Best practices for event naming, versioning, upcasting | - |
| Kafka integration | ✅ | | Kafka extension for event publishing | Requires Axon Server or Axon connector |
| Login-specific events | | ❌ | - | Generic framework — auth events must be user-defined |
| CloudEvents | | ❌ | - | Custom metadata format |
| Transactional outbox | ✅ | | Built-in outbox support | Tied to Axon infrastructure |
| Schema versioning | ✅ | | Event upcasting for schema evolution | Complex upcasting API |

**Verdict**: Tham khảo event design patterns
**Recommendation**: Tham khảo pattern (event naming, schema versioning, success/failure separation)
**Reasoning**: Axon provides excellent patterns for event design (immutable data classes, past-tense naming, versioning strategy) but is too heavy as a dependency. Our existing EventService + EventEnvelope already follows the core patterns.

### ORY Kratos — Gap Analysis

**Overall Score**: 7.30 / 10
**URL**: https://github.com/ory/kratos

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Session events | ✅ | | Rich session.issued event with identity + device context | - |
| Login flow events | ✅ | | Flow-based event model (login flow completion) | - |
| Device context | ✅ | | Device info in session payload | - |
| Event persistence | | ❌ | - | Webhook-only, no built-in event store |
| Kafka publishing | | ❌ | - | Webhooks, not Kafka |
| CloudEvents | | ❌ | - | Custom webhook format |
| Failure events | ⚠️ | | Limited — flow errors, not structured login failures | - |
| MFA context | ✅ | | AAL (Authentication Assurance Level) in session | - |

**Verdict**: Tham khảo payload design (AAL concept, session context)
**Recommendation**: Tham khảo pattern
**Reasoning**: Kratos's AAL (Authentication Assurance Level) concept is valuable — maps to MFA status tracking. Device context in session events is a good reference. Go language prevents direct code reuse.

### Spring Authorization Server — Gap Analysis

**Overall Score**: 7.25 / 10
**URL**: https://github.com/spring-projects/spring-authorization-server

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Authentication events | ✅ | | Spring Security event hierarchy (success + typed failures) | Minimal payload, in-process only |
| Spring Boot compatibility | ✅ | | Native Spring ecosystem | - |
| Event persistence | | ❌ | - | In-process events only — no persistence |
| Kafka publishing | | ❌ | - | Must add custom listener |
| Enriched payload | | ❌ | - | Only wraps Authentication object |
| Device/IP context | | ❌ | - | Not included in events |
| Typed failure events | ✅ | | Separate event classes per failure type | Inheritance-heavy, hard to serialize |

**Verdict**: Tham khảo failure event classification
**Recommendation**: Tham khảo pattern (failure type hierarchy concept)
**Reasoning**: Spring Security's typed failure events (BadCredentials, Locked, Expired, etc.) provide a classification model. However, we prefer a single `UserLoginFailedEvent` with a `failureReason` enum discriminator (matching our existing `ValidationFailureReason` pattern) rather than separate failure event classes.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Keycloak | 8.45/10 | Tham khảo event type enum + field design | EventType classification, event field design |
| 🥈 2 | Axon Framework | 7.60/10 | Tham khảo event design patterns | Event naming, schema versioning, immutable events |
| 🥉 3 | ORY Kratos | 7.30/10 | Tham khảo payload design (AAL, device context) | AAL concept, session-based events |
| 4 | Spring Auth Server | 7.25/10 | Tham khảo failure classification | Typed failure event concept |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Build from scratch** following existing EventService patterns, informed by Keycloak's event model | Our system already has superior event infrastructure (CloudEvents envelope, transactional outbox, Kafka relay). None of the evaluated projects provide a login event model that can be directly adopted. The gap is specifically in creating `UserLoggedInEvent` and `UserLoginFailedEvent` domain events that integrate with our existing `EventService.record()` pipeline. | Keycloak's event model (reference), Axon's event design patterns (reference), project's existing TokenIssuedEvent + UserRegisteredEvent (direct patterns to follow) |

---

> **Sources**: All project URLs verified at 2025-08-22
> **Next step**: Comparison Analysis (comparison_analysis.md)
