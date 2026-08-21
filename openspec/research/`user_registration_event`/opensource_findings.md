# Kết quả tìm kiếm Open Source: User Registration Event

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Registration Event (Event Sourcing + CQRS for Authentication) |
| **Ngày tìm kiếm** | 2025-08-21 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin/Java, Spring Boot 3.x, PostgreSQL, Kafka, CQRS |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"event sourcing CQRS Spring Boot Kotlin authentication"` | 3 relevant repos | Axon Framework samples, custom implementations |
| 2 | `"domain event user registration Spring Kafka"` | 4 relevant repos | Various microservice templates with event publishing |
| 3 | `"CloudEvents specification Java SDK"` | 2 relevant repos | CloudEvents SDK and envelope standard |
| 4 | `"transactional outbox pattern Spring Boot PostgreSQL"` | 3 relevant repos | Outbox pattern implementations |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Axon Framework | https://github.com/AxonFramework/AxonFramework | ~8.5k | Active (weekly) | Apache 2.0 | ✅ Có |
| 2 | EventStoreDB Client (JVM) | https://github.com/EventStore/EventStoreDB-Client-Java | ~100 | Active (monthly) | Apache 2.0 | ✅ Có |
| 3 | CloudEvents Java SDK | https://github.com/cloudevents/sdk-java | ~400 | Active (monthly) | Apache 2.0 | ✅ Có |
| 4 | Spring Modulith | https://github.com/spring-projects/spring-modulith | ~700 | Active (weekly) | Apache 2.0 | ✅ Có |
| 5 | Eventuate Tram | https://github.com/eventuate-tram/eventuate-tram-core | ~400 | Active (quarterly) | Apache 2.0 | ❌ Không (complex multi-module, heavy dependency) |
| 6 | Debezium (CDC) | https://github.com/debezium/debezium | ~11k | Active (weekly) | Apache 2.0 | ❌ Không (infrastructure-level, not library for events) |
| 7 | Marten (.NET) | https://github.com/JasperFx/marten | ~2.7k | Active | MIT | ❌ Không (.NET ecosystem, not JVM) |
| 8 | event-sourcing-cqrs-sample | Various GitHub samples | ~50-200 | Varies | MIT/Apache | ❌ Không (tutorial-grade, not production-ready) |

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
| Feature completeness | 10 | 20% | 2.00 | Full ES+CQRS: aggregates, event store, sagas, projections, upcasters, snapshots |
| Applicability | 5 | 15% | 0.75 | Java/Kotlin Spring Boot compatible but heavy framework; requires Axon Server or full adoption |
| Activity | 9 | 15% | 1.35 | Weekly commits, professional team (AxonIQ), regular releases |
| Documentation | 9 | 15% | 1.35 | Comprehensive docs at docs.axoniq.io, tutorials, reference guide |
| Code quality | 9 | 15% | 1.35 | Well-tested, clean modular architecture, enterprise-grade |
| Community | 9 | 10% | 0.90 | ~8.5k stars, active Discord, commercial support available |
| Popularity | 9 | 10% | 0.90 | Industry standard for JVM event sourcing, used by banks/enterprises |
| **Tổng điểm** | | | **8.60/10** | |

#### EventStoreDB Client (JVM)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Purpose-built event store with streams, subscriptions, projections |
| Applicability | 4 | 15% | 0.60 | Requires external EventStoreDB server; adds operational complexity |
| Activity | 7 | 15% | 1.05 | Monthly commits, maintained by Event Store Ltd |
| Documentation | 7 | 15% | 1.05 | Good docs at eventstore.com, but JVM client docs less polished |
| Code quality | 7 | 15% | 1.05 | Decent test coverage, gRPC-based client |
| Community | 4 | 10% | 0.40 | ~100 stars (JVM client), main EventStoreDB repo has ~5k |
| Popularity | 6 | 10% | 0.60 | Popular in .NET world, growing JVM adoption |
| **Tổng điểm** | | | **6.35/10** | |

#### CloudEvents Java SDK

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Event envelope standard only — not an event store; provides type, source, id, time, schema |
| Applicability | 8 | 15% | 1.20 | Lightweight SDK, easy to integrate with Kafka; complements existing EventPublisher |
| Activity | 7 | 15% | 1.05 | Monthly commits, CNCF project, stable specification (v1.0.2) |
| Documentation | 8 | 15% | 1.20 | CNCF spec is well-documented; SDK has good README + samples |
| Code quality | 8 | 15% | 1.20 | Clean implementation, good test coverage |
| Community | 6 | 10% | 0.60 | ~400 stars (SDK); spec itself is widely adopted across cloud providers |
| Popularity | 8 | 10% | 0.80 | Industry standard endorsed by AWS, Azure, GCP, Knative |
| **Tổng điểm** | | | **7.45/10** | |

#### Spring Modulith

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Application events, event publication log (outbox), event externalization to Kafka |
| Applicability | 9 | 15% | 1.35 | Native Spring Boot integration, minimal config, works with Spring Data JPA |
| Activity | 9 | 15% | 1.35 | Weekly commits, official Spring team project, Oliver Drotbohm maintainer |
| Documentation | 8 | 15% | 1.20 | Good reference docs, Spring team blog posts, conference talks |
| Code quality | 9 | 15% | 1.35 | Spring team quality standards, comprehensive tests |
| Community | 7 | 10% | 0.70 | ~700 stars, growing rapidly as part of Spring ecosystem |
| Popularity | 7 | 10% | 0.70 | Rapidly growing adoption, recommended by Spring team |
| **Tổng điểm** | | | **8.05/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Axon Framework | 10 | 5 | 9 | 9 | 9 | 9 | 9 | **8.60** |
| 2 | EventStoreDB Client | 8 | 4 | 7 | 7 | 7 | 4 | 6 | **6.35** |
| 3 | CloudEvents SDK | 7 | 8 | 7 | 8 | 8 | 6 | 8 | **7.45** |
| 4 | Spring Modulith | 7 | 9 | 9 | 8 | 9 | 7 | 7 | **8.05** |

---

## 4. Gap Analysis chi tiết

### Axon Framework — Gap Analysis

**Overall Score**: 8.60 / 10
**URL**: https://github.com/AxonFramework/AxonFramework

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Event Store | ✅ | | Built-in JPA/JDBC event store | - |
| Event Versioning (Upcasters) | ✅ | | Native upcaster support | - |
| CQRS Command/Query Bus | ✅ | | Full command bus, query bus, saga | - |
| Kafka Integration | ✅ | | Axon Kafka extension | - |
| Aggregate Root Pattern | ✅ | | @Aggregate, @CommandHandler, @EventSourcingHandler | - |
| Lightweight Integration | | ❌ | - | Full framework adoption required; heavy lock-in |
| Compatibility with existing eventsourcing-utils | | ❌ | - | Would replace existing Command/CommandHandler types |
| PostgreSQL Event Store | ✅ | | JPA-based, works with PostgreSQL | Requires Axon-specific table schema |
| Scalability | ✅ | | Axon Server for distributed, or standalone | Axon Server adds infrastructure cost |

**Verdict**: Tham khảo pattern — excellent reference architecture but too heavy for incremental adoption
**Recommendation**: Tham khảo pattern
**Reasoning**: Axon Framework is the gold standard for JVM event sourcing but requires full framework adoption. The project already has its own `eventsourcing-utils` library with Command/CommandHandler. Adopting Axon would mean replacing the existing CQRS infrastructure, which is not justified for the scope of this feature.

### EventStoreDB Client — Gap Analysis

**Overall Score**: 6.35 / 10
**URL**: https://github.com/EventStore/EventStoreDB-Client-Java

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Dedicated Event Store | ✅ | | Purpose-built for event sourcing | - |
| Stream Subscriptions | ✅ | | Persistent subscriptions, catch-up subscriptions | - |
| Projections | ✅ | | Server-side projections with JavaScript | - |
| PostgreSQL Compatibility | | ❌ | - | Requires separate EventStoreDB server |
| Spring Boot Starter | | ❌ | - | No official Spring Boot starter |
| Kafka Integration | | ❌ | - | Separate system; would need bridge to Kafka |
| Lightweight | | ❌ | - | Adds significant infrastructure (another database) |

**Verdict**: Không phù hợp
**Recommendation**: Bỏ qua
**Reasoning**: EventStoreDB adds significant operational complexity (another database to manage). The project already uses PostgreSQL + Kafka. A PostgreSQL-based event store table is more aligned with existing infrastructure.

### CloudEvents Java SDK — Gap Analysis

**Overall Score**: 7.45 / 10
**URL**: https://github.com/cloudevents/sdk-java

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Standard Event Envelope | ✅ | | CNCF standard: id, source, type, time, datacontenttype, specversion | - |
| Schema Registry Support | ✅ | | dataschema attribute for schema versioning | - |
| Kafka Binding | ✅ | | CloudEvents Kafka binding specification | - |
| Jackson Serialization | ✅ | | Jackson module for CloudEvents | - |
| Event Store | | ❌ | - | Only envelope specification; no storage |
| CQRS Integration | | ❌ | - | Not a CQRS framework; only event format |
| Lightweight | ✅ | | Small library, additive — doesn't replace existing code | - |
| Spring Boot Integration | ⚠️ | | Works with Spring Kafka but no starter | Manual configuration needed |

**Verdict**: Có thể dùng trực tiếp — as event envelope standard
**Recommendation**: Tham khảo pattern (adopt envelope format, optionally use SDK)
**Reasoning**: CloudEvents provides an industry-standard envelope format. The project can adopt the envelope structure (id, source, type, time, specversion, data) without necessarily using the SDK. This gives interoperability and future-proofing.

### Spring Modulith — Gap Analysis

**Overall Score**: 8.05 / 10
**URL**: https://github.com/spring-projects/spring-modulith

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Event Publication Log | ✅ | | Transactional outbox via `@ApplicationModuleListener` + JPA/JDBC publication log | - |
| Kafka Externalization | ✅ | | `spring-modulith-events-kafka` module for externalizing to Kafka | - |
| Spring Native | ✅ | | Native Spring Boot integration, annotation-driven | - |
| Event Completion Tracking | ✅ | | Tracks published/completed events; supports retry for failed publications | - |
| Full Event Sourcing | | ❌ | - | Not a full ES framework; focuses on application events + outbox |
| Event Versioning | | ❌ | - | No built-in upcaster or schema evolution |
| Aggregate Pattern | | ❌ | - | No aggregate root pattern |
| PostgreSQL Event Store | ⚠️ | | Publication log table, not a full event store | Limited to outbox tracking |

**Verdict**: Có thể dùng trực tiếp — for transactional outbox pattern
**Recommendation**: Tham khảo pattern (adopt outbox pattern, potentially use publication log)
**Reasoning**: Spring Modulith's event publication log solves the "fire-and-forget" problem elegantly. It ensures events are persisted in the same transaction as domain changes and then asynchronously published to Kafka. This is a strong fit for the project's needs.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Axon Framework | 8.60/10 | Tham khảo pattern | Architecture patterns, event store design, upcaster concepts |
| 🥈 2 | Spring Modulith | 8.05/10 | Tham khảo pattern / Dùng publication log | Transactional outbox pattern, event externalization to Kafka |
| 🥉 3 | CloudEvents SDK | 7.45/10 | Tham khảo pattern / Adopt envelope | Event envelope standard, schema versioning via dataschema |
| 4 | EventStoreDB Client | 6.35/10 | Bỏ qua | Not suitable — adds infrastructure complexity |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Build from scratch** (inspired by patterns from top projects) | The project already has `eventsourcing-utils` with Command/CommandHandler. Adopting a full framework (Axon) is too disruptive. Best approach: enrich existing `UserRegisteredEvent`, add PostgreSQL event store table (inspired by Axon JPA store), adopt CloudEvents envelope format, implement transactional outbox (inspired by Spring Modulith) | Codebase analysis confirms existing CQRS infrastructure; external frameworks would conflict |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-08-21
> **Next step**: Comparison Analysis (comparison_analysis.md)
