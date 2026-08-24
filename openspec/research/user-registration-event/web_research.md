# Kết quả nghiên cứu Internet: User Registration Event

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Registration Event |
| **Ngày nghiên cứu** | 2025-08-22 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | user registration event, domain event, event sourcing, CQRS, transactional outbox |
| **Keywords phát triển** | CloudEvents schema, OCSF authentication event, fire-and-forget pattern, event upcasting, registration failure monitoring |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"user registration event sourcing domain event best practices"` | Multiple articles on CQRS/ES with Spring Boot + Kafka. Common pattern: domain event as data class, event store append, outbox relay | event envelope, aggregate versioning, event upcasting |
| 2 | `"transactional outbox pattern user registration microservices"` | Chris Richardson's microservices.io patterns, Debezium CDC, polling-based outbox relay | CDC, polling relay, idempotent consumer |
| 3 | `"CloudEvents user registration event schema design"` | CloudEvents spec 1.0 — standardized envelope format (id, source, type, specversion, time, data) | specversion, datacontenttype, subject |

**Takeaways Iteration 1:**
- Industry consensus: User registration events should follow CloudEvents or CloudEvents-inspired envelope with rich payload
- Transactional outbox is the gold standard for reliable event publishing in microservices
- Fire-and-forget is debated — some argue registration events SHOULD be transactional (user creation + event recording = atomic)
- Schema versioning is critical for long-lived event stores

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://codewithyoha.com/blogs/mastering-event-sourcing-cqrs-with-spring-boot-apache-kafka | Mastering Event Sourcing & CQRS with Spring Boot & Kafka | Complete ES/CQRS implementation with Spring Boot + Kafka. UserCreatedEvent as aggregate event, event store table structure, Kafka producer configuration | 9 |
| 2 | https://java.elitedev.in/java/event-sourcing-and-cqrs-with-spring-boot-and-kafka-complete-implementation-guide-d05366b5/ | Event Sourcing and CQRS with Spring Boot and Kafka | Command → Event → Store → Publish pipeline. Emphasizes idempotent event processing, correlation ID threading | 8 |
| 3 | https://harshad-sonawane.com/blog/implementing-cqrs-and-event-sourcing-in-spring-boot/ | Implementing CQRS and Event Sourcing in Spring Boot | PostgreSQL event store + Kafka. Schema versioning via schemaVersion field in event envelope | 8 |
| 4 | https://medium.com/@giovanni.alluvatti/cqrs-and-event-sourcing-with-spring-boot-and-kafka-part-i-f7c257e01e57 | CQRS and Event Sourcing with Spring Boot and Kafka — Part I | Command side architecture. Aggregate → Command Handler → Domain Event pattern. Registration as a command | 7 |

**Takeaways Iteration 2:**
- Approach 1: **Transactional event recording** — event store write + outbox insert in same DB transaction as entity creation. Guarantees consistency. Trade-off: event recording failure rolls back user creation
- Approach 2: **Fire-and-forget event recording** — event recording wrapped in try/catch, exception logged but not rethrown. Trade-off: potential event loss (compensated by retry/reconciliation)
- Approach 3: **Hybrid** — critical events (user creation) use transactional; non-critical (analytics) use fire-and-forget
- Consensus on registration events: **transactional is preferred** because user creation without event recording means downstream services never learn about the new user (profile provisioning, welcome email, analytics)

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"OCSF authentication event schema user registration"` (từ gap: event schema standards) | OCSF (Open Cybersecurity Schema Framework) defines Account Change Activity (class_uid=3001) for registration events with standardized fields: user.name, user.email_addr, activity_id=1 (Create), status_id, metadata.correlation_uid | ✅ |
| 2 | `"Keycloak REGISTER event details fields"` (verify event model) | Keycloak EventType.REGISTER includes: realmId, clientId, userId, username, email, ipAddress, authDetails. EventType.REGISTER_ERROR adds error field | ✅ |
| 3 | `"Auth0 post user registration action trigger"` (verify commercial patterns) | Auth0 Post User Registration Action: event.user (user_id, email, username, phone_number, created_at), event.connection, event.request (ip, geoip, userAgent) | ✅ |
| 4 | `"event sourcing schema versioning upcasting best practices"` (deep dive on versioning) | Axon @Revision annotation, Event Upcaster chain pattern, Protobuf/Avro for schema evolution. Recommendation: include schemaVersion in envelope, use backward-compatible changes only | ✅ |

**Stop reason**: All research questions answered, diminishing returns on additional searches

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Article | Mastering Event Sourcing & CQRS with Spring Boot & Kafka | https://codewithyoha.com/blogs/mastering-event-sourcing-cqrs-with-spring-boot-apache-kafka | Complete ES/CQRS with UserCreatedEvent, event store, Kafka | 9 | Full implementation guide | Java-focused, not Kotlin |
| 2 | Article | Event Sourcing and CQRS with Spring Boot and Kafka Guide | https://java.elitedev.in/java/event-sourcing-and-cqrs-with-spring-boot-and-kafka-complete-implementation-guide-d05366b5/ | Idempotent event processing, correlation ID pattern | 8 | Practical examples | Lacks failure event pattern |
| 3 | Article | CQRS and Event Sourcing in Spring Boot | https://harshad-sonawane.com/blog/implementing-cqrs-and-event-sourcing-in-spring-boot/ | Schema versioning, PostgreSQL event store | 8 | Schema versioning focus | Basic implementation |
| 4 | Spec | OCSF Account Change Activity (class_uid=3001) | https://schema.ocsf.io/1.0.0/classes/account_change | Standardized security event schema for account lifecycle | 8 | Industry standard fields | Over-complex for simple use cases |
| 5 | Docs | Keycloak Event Listener SPI | https://www.keycloak.org/docs/latest/server_development/#_events | REGISTER/REGISTER_ERROR event types, details map | 8 | Battle-tested event model | Different architecture |
| 6 | Docs | Auth0 Post User Registration Action | https://auth0.com/docs/customize/actions/flows-and-triggers/post-user-registration-flow | Post-registration hook with user/connection/request context | 7 | Rich event context | Commercial/proprietary |
| 7 | Spec | CloudEvents Specification 1.0 | https://cloudevents.io/ | Standard envelope: id, type, source, specversion, time, datacontenttype, subject | 7 | Industry standard | Envelope only, not domain events |
| 8 | Article | CQRS and Event Sourcing with Spring Boot and Kafka Part I | https://medium.com/@giovanni.alluvatti/cqrs-and-event-sourcing-with-spring-boot-and-kafka-part-i-f7c257e01e57 | Command side architecture, aggregate pattern | 7 | Clear architecture | Part I only |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Keycloak (Event SPI) | SPI-based event listener pattern. EventType enum classifies REGISTER, REGISTER_ERROR events. Details map captures user/realm/client context. | EventListenerProviderFactory, EventType enum, Event details map, AdminEvent | Battle-tested taxonomy, extensible listener model | Open source, mature, Red Hat-backed | Synchronous listeners, no event store persistence by default | No transactional outbox, no schema versioning |
| Auth0 (Actions) | Serverless hook triggered post-registration. Provides rich event context (user, connection, request with IP/geo). | Post User Registration Action, event.user, event.connection, event.request | Rich context (geo, device), automatic trigger | Seamless integration, auto-triggered | Proprietary, serverless execution limits | No event store, no schema versioning |
| OCSF Schema | Standardized cybersecurity event schema. Account Change Activity (class_uid=3001) covers user lifecycle events. | activity_id=1 (Create), status_id, user object, metadata.correlation_uid | Industry standard, interoperable | Standard fields, SIEM-compatible | Complex schema, over-engineered for simple cases | Focused on security, not domain events |

### So sánh tính năng chi tiết

| Feature | Keycloak | Auth0 | OCSF | Custom Build (auth-service) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Registration Success Event | ✅ | ✅ | ✅ | ✅ (existing) | ⭐ Must |
| Registration Failure Event | ✅ | ❌ | ✅ | ❓ (to be built) | ⭐ Must |
| Enriched Payload (identity + device) | ✅ | ✅ | ✅ | ✅ (existing) | ⭐ Must |
| Event Store Persistence | ❌ | ❌ | N/A | ✅ (existing) | ⭐ Must |
| Transactional Outbox | ❌ | ❌ | N/A | ✅ (existing) | ⭐ Must |
| Kafka Topic Publishing | ⚠️ | ❌ | N/A | ✅ (existing) | ⭐ Must |
| Fire-and-Forget Pattern | ❌ | ✅ | N/A | ❓ (to be decided) | Nice to have |
| Schema Versioning | ❌ | ❌ | ❌ | ⚠️ (basic schemaVersion field) | Nice to have |
| Correlation ID | ❌ | ❌ | ✅ | ✅ (existing) | ⭐ Must |
| Geo-Location Enrichment | ❌ | ✅ | ✅ | ❌ | Optional |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | Transactional Event Recording | Event store + outbox write in same @Transactional as entity creation | Atomic consistency — user creation and event recording succeed or fail together | Event recording failure prevents user creation | Registration event is critical for downstream provisioning (welcome email, profile creation) | ES/CQRS best practices |
| 2 | Fire-and-Forget Event Recording | Wrap EventService.record() in try/catch, log failure, continue | User creation never blocked by event infrastructure issues | Potential event loss — downstream services miss registration | Event recording failure is acceptable (compensated by reconciliation job) | LoginEventRecorder pattern |
| 3 | Hybrid: Transactional + Compensating | Transactional for event store, fire-and-forget for notifications | Best of both worlds — guaranteed audit trail + resilient notification | More complex, two recording paths | When audit trail is mandatory but notification failure is acceptable | Keycloak + Eventuate pattern |
| 4 | Dedicated Event Recorder Helper | Extract EventService.record() call into RegistrationEventRecorder @Component | Consistent pattern with LoginEventRecorder/TokenEventRecorder, single responsibility | One more class to maintain | When registration event recording needs specific error handling or enrichment logic | Auth-service internal pattern |
| 5 | Failure Event Classification | Typed enum for registration failure reasons (DUPLICATE_USERNAME, DUPLICATE_EMAIL, INVALID_DOMAIN, WEAK_PASSWORD, etc.) | Structured monitoring, alerting on specific failure types | More code, needs to be maintained as failure reasons evolve | When security monitoring needs structured failure data | Keycloak EventType.REGISTER_ERROR |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Should geo-location be included in UserRegisteredEvent? | ✅ | Auth0 includes geo, but auth-service has no geo-IP service. Would require new dependency | Low — can be added later as optional field |
| 2 | Should device fingerprint be included in UserRegisteredEvent (like UserLoggedInEvent)? | ✅ | UserLoggedInEvent has deviceFingerprint field. RegisterHandler doesn't currently collect this. | Low — registration typically doesn't do device fingerprinting |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-08-22
> **Next step**: Comparison Analysis (comparison_analysis.md)
