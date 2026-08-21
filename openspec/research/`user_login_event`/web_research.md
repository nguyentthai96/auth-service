# Kết quả nghiên cứu Internet: User Login Event

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Login Event (Event Sourcing + CQRS) |
| **Ngày nghiên cứu** | 2025-08-22 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | `user login event`, `authentication event sourcing`, `login domain event`, `audit trail login` |
| **Keywords phát triển** | `login event enrichment`, `failed login event security`, `CloudEvents authentication`, `device fingerprint event`, `geo-IP login tracking`, `SIEM event format` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"how to implement user login event sourcing best practices"` | Login events are fundamental in ES architectures — recommended to emit both success and failure events for complete audit trail | `login success event`, `login failure event`, `audit event` |
| 2 | `"authentication event enrichment payload design patterns"` | Best practice: include IP, device, geo, timestamp, MFA status, session context in login events for downstream analytics and security monitoring | `event enrichment`, `context metadata`, `security event` |
| 3 | `"login event vs session event comparison CQRS"` | Login events capture the authentication moment; session events track ongoing session lifecycle. Both are needed for complete observability. | `session lifecycle event`, `authentication moment` |

**Takeaways Iteration 1:**
- Login events in ES should capture the FULL authentication context at the moment of login
- Both success and failure events are critical — failure events are actually MORE important for security monitoring
- Event payload should be self-contained (not require joining with other data to be useful)
- Kafka topic naming convention: `iam.user.logged_in` (past tense, domain-qualified)

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://martinfowler.com/eaaDev/EventSourcing.html | Event Sourcing (Martin Fowler) | Events should be immutable facts; login is a natural domain event — captures "User X authenticated at time T from device D" | 9 |
| 2 | https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing | Event Sourcing pattern (Microsoft Azure) | Event store should be append-only, events should carry correlation IDs for distributed tracing | 8 |
| 3 | https://microservices.io/patterns/data/event-sourcing.html | Event Sourcing (microservices.io) | Transactional outbox pattern for reliable event publishing — exactly what project implements | 8 |
| 4 | https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html | OWASP Logging Cheat Sheet | Security events must include: who (userId), what (action), when (timestamp), where (IP, geo), outcome (success/fail), why (failure reason) | 9 |
| 5 | https://www.elastic.co/guide/en/ecs/current/ecs-event.html | Elastic Common Schema (ECS) - Event | Industry-standard event schema: event.category=authentication, event.type=start, event.outcome=success/failure — useful for SIEM integration | 7 |

**Takeaways Iteration 2:**
- **OWASP recommends**: Login events should always include WHO, WHAT, WHEN, WHERE, OUTCOME, and WHY (for failures)
- **Elastic ECS**: Standardized schema for security events used by SIEM tools — aligning event payload with ECS facilitates integration
- **Microsoft guidance**: Events should be self-describing with schema version for forward/backward compatibility
- **Correlation ID**: Essential for tracing login flow across services (auth → session → audit → notification)

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"login event enrichment device fingerprint geo-IP"` (từ gap trong Iter 2) | Device fingerprint should be a hash (SHA-256), not raw data. Geo-IP resolution should happen at event creation time, not downstream — avoids stale IP-to-location mapping | ✅ |
| 2 | `"MFA login event — when to emit"` (verify approach) | Emit event AFTER full authentication completes (including MFA verification). If MFA is pending, emit only after MFA success. Failed MFA should emit UserLoginFailedEvent with reason=MFA_FAILED | ✅ |
| 3 | `"login event performance impact high frequency"` (performance concern) | Login events are typically 100-1000x less frequent than read operations. Event store append is O(1). Outbox polling is batched. No performance concern for production workloads. | ✅ |

**Stop reason**: All research questions answered, diminishing returns reached

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Article | Event Sourcing (Martin Fowler) | https://martinfowler.com/eaaDev/EventSourcing.html | Foundational ES patterns, login as natural domain event | 9 | Authoritative, clear explanations | High-level, no code |
| 2 | Docs | Event Sourcing pattern (Azure) | https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing | Practical guidance on event store design, correlation IDs | 8 | Practical, battle-tested | Azure-specific examples |
| 3 | Docs | OWASP Logging Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html | Security event payload requirements (WHO/WHAT/WHEN/WHERE) | 9 | Industry security standard | Not ES-specific |
| 4 | Docs | Elastic Common Schema - Event | https://www.elastic.co/guide/en/ecs/current/ecs-event.html | Standardized security event schema for SIEM integration | 7 | Industry standard schema | ECS-specific naming |
| 5 | Article | Transactional Outbox (microservices.io) | https://microservices.io/patterns/data/event-sourcing.html | Outbox pattern for reliable event publishing | 8 | Clear pattern description | No Kotlin examples |
| 6 | Docs | Spring Kafka Documentation | https://docs.spring.io/spring-kafka/docs/current/reference/html/ | Kafka producer patterns, topic naming, partitioning | 7 | Directly applicable | Not ES-focused |
| 7 | Docs | CloudEvents Specification | https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md | Standard envelope format — project already follows | 6 | Interoperability standard | Only envelope, not domain |
| 8 | Article | CQRS Pattern (Microsoft) | https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs | Command/Query segregation, event-driven state changes | 7 | Comprehensive CQRS guide | Not login-specific |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Auth0 (Login Events) | SaaS — emits login events via Log Streams to SIEM/webhook | Enriched login events with device, geo, risk score | Zero implementation, rich analytics | Cloud-native, automatic enrichment | Vendor lock-in, no self-hosted ES control | No event store, no CQRS |
| Keycloak (Events) | Self-hosted — built-in event system with LOGIN/LOGIN_ERROR types | Event listeners, admin console event viewer, exportable event log | Self-hosted, mature event system | Free, extensive event types | No transactional outbox, events in separate DB | No Kafka outbox integration |
| Firebase Auth (Events) | SaaS — Cloud Functions triggered on auth events | beforeSignIn/afterSignIn blocking functions | Serverless, auto-scaling | Easy integration | Google ecosystem lock-in | No event store, no CQRS |

### So sánh tính năng chi tiết

| Feature | Auth0 | Keycloak | Custom Build (Project) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| Login success event | ✅ | ✅ | ✅ | ⭐ Must |
| Login failed event | ✅ | ✅ | ✅ | ⭐ Must |
| Device/IP enrichment | ✅ | ⚠️ | ✅ | ⭐ Must |
| Event store persistence | ❌ | ⚠️ | ✅ | ⭐ Must |
| Transactional outbox | ❌ | ❌ | ✅ | ⭐ Must |
| Kafka integration | ✅ | ⚠️ (plugin) | ✅ | ⭐ Must |
| CQRS command integration | ❌ | ❌ | ✅ | ⭐ Must |
| Schema versioning | ❌ | ❌ | ✅ | Nice to have |
| CloudEvents envelope | ❌ | ❌ | ✅ | Nice to have |
| Correlation ID | ❌ | ❌ | ✅ | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Event Store + Outbox (project pattern)** | Record event to event_store + outbox in same TX, OutboxPoller relays to Kafka | Transactional consistency, replay capability, reliable delivery | Slight latency (polling interval), more DB writes | Need audit trail + async downstream consumers | microservices.io, project codebase |
| 2 | **Direct Kafka publish** | Publish login event directly to Kafka topic (fire-and-forget or with retry) | Low latency, simple implementation | No event store, no replay, potential data loss on Kafka failures | Low-criticality events, no audit requirement | Spring Kafka docs |
| 3 | **Application event + externalization** | Use Spring ApplicationEventPublisher with event log for externalization | Spring-native, simple, no framework dependency | No full event store, limited replay capability | Monolith or modular monolith with eventual Kafka | Spring Modulith docs |
| 4 | **CDC-based (Debezium)** | Write event to DB, Debezium captures change and publishes to Kafka | No outbox polling needed, low latency | Operational complexity (Debezium cluster), more infrastructure | Large-scale systems with dedicated CDC infrastructure | Debezium docs |

**Selected Approach**: #1 — Event Store + Outbox (matches existing project pattern)

**Reasoning**: The project already implements this pattern successfully for `UserRegisteredEvent`. Extending to `UserLoggedInEvent` requires minimal code changes — just a new event class and a `eventService.record()` call in `LoginHandler`.

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Should geo-IP resolution be inline or async? | ✅ | Decision depends on latency budget — inline adds ~5ms per login but ensures complete event payload | Low — can start with IP only, add geo later |
| 2 | Should login events include user roles/permissions? | ✅ | Trade-off: richer payload vs. event size. Roles can change between login and event consumption. | Low — roles available via separate query |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-08-22
> **Next step**: Comparison Analysis (comparison_analysis.md)
