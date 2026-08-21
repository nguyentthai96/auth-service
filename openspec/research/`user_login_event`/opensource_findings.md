# Kết quả tìm kiếm Open Source: User Login Event

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Login Event (Event Sourcing + CQRS for Authentication Login) |
| **Ngày tìm kiếm** | 2025-08-22 |
| **Số dự án tìm thấy** | 7 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin/Java, Spring Boot 3.x, PostgreSQL, Kafka, CQRS |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"event sourcing CQRS authentication login Spring Boot"` | 3 relevant repos | Axon Framework samples, custom ES implementations |
| 2 | `"domain event login audit trail Kafka Spring"` | 3 relevant repos | Microservice templates with login event publishing |
| 3 | `"CloudEvents Java SDK login event schema"` | 2 relevant repos | CloudEvents SDK, Spring Modulith event support |
| 4 | `"transactional outbox login event Spring Boot PostgreSQL"` | 2 relevant repos | Outbox pattern implementations matching project |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Axon Framework | https://github.com/AxonFramework/AxonFramework | ~8.5k | Active (weekly) | Apache 2.0 | ✅ Có |
| 2 | Spring Modulith | https://github.com/spring-projects/spring-modulith | ~700 | Active (weekly) | Apache 2.0 | ✅ Có |
| 3 | CloudEvents Java SDK | https://github.com/cloudevents/sdk-java | ~400 | Active (monthly) | Apache 2.0 | ✅ Có |
| 4 | Eventuate Tram | https://github.com/eventuate-tram/eventuate-tram-core | ~400 | Active (quarterly) | Apache 2.0 | ✅ Có |
| 5 | Debezium (CDC) | https://github.com/debezium/debezium | ~11k | Active (weekly) | Apache 2.0 | ❌ Không (infrastructure-level CDC, not event design library) |
| 6 | EventStoreDB Client (JVM) | https://github.com/EventStore/EventStoreDB-Client-Java | ~100 | Active (monthly) | Apache 2.0 | ❌ Không (requires external EventStoreDB — project uses PostgreSQL) |
| 7 | Marten (.NET) | https://github.com/JasperFx/marten | ~2.7k | Active | MIT | ❌ Không (.NET ecosystem, not JVM) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Axon Framework

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Full ES+CQRS: aggregates, event store, sagas, projections, upcasters, snapshots — handles login events natively |
| Applicability | 4 | 15% | 0.60 | Java/Kotlin compatible but requires full framework adoption; project uses lightweight custom eventsourcing-utils |
| Activity | 9 | 15% | 1.35 | Weekly commits, professional team (AxonIQ), regular releases |
| Documentation | 9 | 15% | 1.35 | Comprehensive docs at docs.axoniq.io, tutorials, reference guide |
| Code quality | 9 | 15% | 1.35 | Well-tested, clean modular architecture, enterprise-grade |
| Community | 9 | 10% | 0.90 | ~8.5k stars, active Discord, commercial support available |
| Popularity | 9 | 10% | 0.90 | Industry standard for JVM event sourcing |
| **Tổng điểm** | | | **8.45/10** | |

#### Spring Modulith

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 6 | 20% | 1.20 | Application event publication log, event externalization to Kafka/AMQP — no full event store |
| Applicability | 8 | 15% | 1.20 | Native Spring Boot integration, same stack, lightweight — fits project philosophy |
| Activity | 9 | 15% | 1.35 | Weekly commits, Spring team maintained |
| Documentation | 8 | 15% | 1.20 | Good Spring reference docs, examples |
| Code quality | 9 | 15% | 1.35 | Spring-quality test coverage, modular design |
| Community | 6 | 10% | 0.60 | ~700 stars, growing adoption |
| Popularity | 7 | 10% | 0.70 | Growing rapidly with Spring ecosystem |
| **Tổng điểm** | | | **7.60/10** | |

#### CloudEvents Java SDK

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 5 | 20% | 1.00 | Event envelope standardization only — no store, no outbox, no CQRS |
| Applicability | 7 | 15% | 1.05 | Standard envelope format — project already uses CloudEvents-inspired EventEnvelope |
| Activity | 7 | 15% | 1.05 | Monthly commits, CNCF sandbox project |
| Documentation | 7 | 15% | 1.05 | Spec is well-documented, SDK docs adequate |
| Code quality | 8 | 15% | 1.20 | Good test coverage, clean API |
| Community | 5 | 10% | 0.50 | ~400 stars, niche but standards-focused |
| Popularity | 6 | 10% | 0.60 | Adopted by major cloud providers |
| **Tổng điểm** | | | **6.45/10** | |

#### Eventuate Tram

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Transactional outbox, message publishing, event sourcing — close to project needs |
| Applicability | 5 | 15% | 0.75 | Java/Spring compatible but requires Eventuate infrastructure; heavy multi-module dependency |
| Activity | 5 | 15% | 0.75 | Quarterly commits, maintained by Chris Richardson's team |
| Documentation | 6 | 15% | 0.90 | Good conceptual docs but less practical examples |
| Code quality | 7 | 15% | 1.05 | Decent tests, but complex multi-module structure |
| Community | 5 | 10% | 0.50 | ~400 stars, niche community |
| Popularity | 5 | 10% | 0.50 | Known through microservices.io, limited production adoption |
| **Tổng điểm** | | | **6.05/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Axon Framework | 10 | 4 | 9 | 9 | 9 | 9 | 9 | **8.45** |
| 2 | Spring Modulith | 6 | 8 | 9 | 8 | 9 | 6 | 7 | **7.60** |
| 3 | CloudEvents SDK | 5 | 7 | 7 | 7 | 8 | 5 | 6 | **6.45** |
| 4 | Eventuate Tram | 8 | 5 | 5 | 6 | 7 | 5 | 5 | **6.05** |

---

## 4. Gap Analysis chi tiết

### Axon Framework — Gap Analysis

**Overall Score**: 8.45 / 10
**URL**: https://github.com/AxonFramework/AxonFramework

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Login Event Modeling | ✅ | | Full aggregate + event modeling with @EventSourcingHandler | - |
| Event Store | ✅ | | Built-in event store with Axon Server or JPA backend | - |
| Transactional Outbox | ✅ | | Built-in event publishing with tracking event processors | - |
| Schema Versioning | ✅ | | Upcasters for event schema evolution | - |
| Enriched Payload | ✅ | | Supports metadata enrichment via message decorators | - |
| Lightweight Integration | | ❌ | - | Requires full Axon adoption; heavy for single event addition |
| Match eventsourcing-utils | | ❌ | - | Different command/event API than project's existing library |
| Incremental Adoption | ⚠️ | | Can add Axon alongside existing code | Significant migration effort |

**Verdict**: Tham khảo pattern — excellent patterns for event design, but too heavy to adopt for a single event addition
**Recommendation**: Tham khảo pattern
**Reasoning**: The project already has a working ES infrastructure (EventService, EventStorePort, OutboxPort). Adopting Axon would require replacing the entire foundation. However, Axon's event enrichment patterns (metadata, correlation ID, event versioning) are excellent references for the custom UserLoggedInEvent design.

### Spring Modulith — Gap Analysis

**Overall Score**: 7.60 / 10
**URL**: https://github.com/spring-projects/spring-modulith

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Event Publication Log | ✅ | | ApplicationEventPublisher with persistence | - |
| Kafka Externalization | ✅ | | Event externalization to Kafka via @Externalized | - |
| Spring Native | ✅ | | Zero-config with Spring Boot auto-configuration | - |
| Full Event Store | | ❌ | - | Event publication log ≠ full event store with replay |
| CQRS Integration | | ❌ | - | No command/query segregation framework |
| Custom EventEnvelope | | ❌ | - | Uses Spring's ApplicationEvent, not CloudEvents envelope |
| Outbox Pattern | ⚠️ | | Event publication log acts as simple outbox | Not full outbox with retry/DLT |

**Verdict**: Tham khảo pattern — good for event externalization approach, but project's custom outbox is more mature
**Recommendation**: Tham khảo pattern
**Reasoning**: Spring Modulith's event externalization concept validates the project's outbox approach. The `@Externalized` annotation pattern could inspire a declarative way to mark events for Kafka publishing. However, the project's existing OutboxPoller with retry/DLT handling is more production-ready.

### CloudEvents Java SDK — Gap Analysis

**Overall Score**: 6.45 / 10
**URL**: https://github.com/cloudevents/sdk-java

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Standard Envelope | ✅ | | CNCF standard, interoperable across services | - |
| Type System | ✅ | | Strong typing with CloudEvent builder | - |
| Kafka Binding | ✅ | | Protocol binding for Kafka headers | - |
| Event Store | | ❌ | - | Only envelope format, no persistence |
| Outbox Pattern | | ❌ | - | Not included |
| Domain Event Design | | ❌ | - | Focuses on wire format, not domain modeling |
| Spring Integration | ⚠️ | | Spring support available | Additional dependency for minor benefit |

**Verdict**: Tham khảo pattern — project already implements CloudEvents-inspired EventEnvelope
**Recommendation**: Tham khảo pattern (format reference only)
**Reasoning**: The project's `EventEnvelope<T>` already follows CloudEvents structure (id, type, source, specversion, time, correlationId). Adding the full SDK would be over-engineering. However, the CloudEvents type naming convention (`com.example.type.v1`) is a useful reference for event type naming.

### Eventuate Tram — Gap Analysis

**Overall Score**: 6.05 / 10
**URL**: https://github.com/eventuate-tram/eventuate-tram-core

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Transactional Outbox | ✅ | | Mature outbox implementation with CDC support | - |
| Message Publishing | ✅ | | Domain event publishing to Kafka/RabbitMQ | - |
| Idempotent Consumer | ✅ | | Built-in deduplication | - |
| Lightweight | | ❌ | - | Complex multi-module dependency tree |
| Match Project Stack | | ❌ | - | Requires Eventuate infrastructure services |
| Custom Event Store | | ❌ | - | Uses CDC-based event relay, not event store |

**Verdict**: Tham khảo pattern — outbox and idempotent consumer patterns are relevant references
**Recommendation**: Tham khảo pattern
**Reasoning**: Project's existing outbox implementation is simpler and adequate. Eventuate's idempotent consumer pattern (processed_events table with event_id + consumer_group) is already implemented in the project (V11 migration).

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Axon Framework | 8.45/10 | Tham khảo pattern | Event modeling patterns, metadata enrichment, upcaster design |
| 🥈 2 | Spring Modulith | 7.60/10 | Tham khảo pattern | Event externalization approach, Spring-native integration patterns |
| 🥉 3 | CloudEvents SDK | 6.45/10 | Tham khảo pattern | Event envelope format, type naming conventions |
| 4 | Eventuate Tram | 6.05/10 | Tham khảo pattern | Outbox pattern, idempotent consumer patterns |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Build from scratch (extend existing)** | Project already has mature ES infrastructure (EventService, EventStorePort, OutboxPort, EventEnvelope). Adding UserLoggedInEvent is an incremental extension, not a new system. All 4 evaluated OS projects would require significant migration or over-engineer the solution. | RegisterHandler already demonstrates the exact pattern needed. LoginHandler simply needs the same EventService.record() call with enriched event payload. |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-08-22
> **Next step**: Comparison Analysis (comparison_analysis.md)
