# Kết quả nghiên cứu Internet: User Registration Event

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Registration Event (Event Sourcing + CQRS for Authentication) |
| **Ngày nghiên cứu** | 2025-08-21 |
| **Số iterations** | 3 |
| **Tổng sources** | 9 unique |
| **Keywords ban đầu** | `event sourcing`, `user registration event`, `domain event`, `CQRS`, `Kafka` |
| **Keywords phát triển** | `transactional outbox`, `CloudEvents`, `event schema versioning`, `upcaster`, `event envelope`, `idempotent consumer`, `aggregate event store PostgreSQL` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"event sourcing domain event design best practices"` rs for multi-version jumps | 8 |
| 6 | https://www.confluent.io/blog/put-several-event-types-kafka-topic/ | Multiple Event Types in One Kafka Topic - Confluent | Topic-per-aggregate-type vs topic-per-event-type. Recommendation: topic-per-aggregate for ordering guarantees, partition by aggregate ID | 8 |

**Takeaways Iteration 2:**
- **CloudEvents Envelope**: Adopt CloudEvents structure for all domain events — provides standard metadata (id, source, type, time, specversion) and enables interoperability with cloud-native tools.
- **Transactional Outbox**: Replace current fire-and-forget with outbox table: `event_outbox(id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at)`. A background poller or CDC reads unpublished events and sends to Kafka.
- **Event Versioning**: Use type-based versioning: `com.ntt.auth.UserRegisteredEvent.v1`, `v2`. Upcaster chain transforms v1 → v2 during replay.
- **Kafka Topic Strategy**: Use `iam.user.events` topic with partitioning by `userId`. All user lifecycle events (registered, deactivated, deleted) on same topic ensures ordering.
- **Conflicting info**: Some sources advocate topic-per-event-type for simplicity (Martin Fowler style), others advocate topic-per-aggregate for ordering (Confluent recommendation). For auth-service, topic-per-aggregate is better because user lifecycle events need ordering guarantees.

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"PostgreSQL event store table design append-only"` (từ gap trong Iter 2) | Standard schema: `event_store(id, aggregate_type, aggregate_id, event_type, version, payload, metadata, timestamp)`. Use `SERIAL` or Snowflake for ordering. Add `sequence_number` for optimistic locking | ✅ |
| 2 | `"idempotent consumer Kafka deduplication Spring Boot"` (verify conflict) | Use `event_id` (UUID) for deduplication. Consumer stores processed event IDs in a `processed_events` table. Check before processing. TTL-based cleanup | ✅ |
| 3 | `"correlation ID distributed tracing event sourcing"` (từ gap trong Iter 1) | Correlation ID = UUID created at entry point (e.g., API controller). Passed through command → event → downstream consumers. Enables distributed tracing via OpenTelemetry | ✅ |

**Stop reason**: All critical questions answered; diminishing returns on further searches.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Article | Event Sourcing - Martin Fowler | https://martinfowler.com/eaaDev/EventSourcing.html | Canonical reference for ES pattern; event = fact, immutable, past tense | 10 | Authoritative, clear explanations | Theoretical — no code samples |
| 2 | Docs | Event Sourcing Pattern - Microsoft Azure | https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing | Production patterns, idempotent handlers, versioning | 9 | Practical, production-focused | Azure-centric examples |
| 3 | Spec | CloudEvents v1.0.2 | https://cloudevents.io/ | CNCF standard event envelope format | 9 | Industry standard, interoperable | Adds envelope overhead |
| 4 | Article | Transactional Outbox Pattern - microservices.io | https://microservices.io/patterns/data/transactional-outbox.html | Solve dual-write with outbox table + poller/CDC | 9 | Solves real production problem | Adds polling complexity |
| 5 | Docs | Event Versioning - AxonIQ | https://docs.axoniq.io/reference-guide/axon-framework/events/event-versioning | Upcaster chain for schema evolution | 8 | Proven approach, clear docs | Axon-specific implementation |
| 6 | Blog | Multiple Event Types in Kafka Topic - Confluent | https://www.confluent.io/blog/put-several-event-types-kafka-topic/ | Topic-per-aggregate vs per-event-type trade-offs | 8 | Data-driven analysis | Confluent bias |
| 7 | Article | Domain Events vs Integration Events | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/domain-events-design-implementation | Domain events (internal) vs integration events (external, versioned) | 8 | Clear distinction | .NET examples |
| 8 | Blog | Event Sourcing in PostgreSQL | https://blog.arkency.com/event-sourcing-with-postgresql/ | PostgreSQL as event store, append-only table design, sequence numbers | 7 | Practical PostgreSQL focus | Ruby examples |
| 9 | Docs | Spring Modulith Event Publication Log | https://docs.spring.io/spring-modulith/reference/events.html | Transactional outbox via Spring Modulith publication log | 8 | Native Spring integration | Requires Spring Modulith dependency |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Debezium CDC | Change Data Capture from DB → Kafka (outbox table reads) | Log-based CDC, PostgreSQL connector, Kafka Connect | No polling needed; low latency | Zero-code event publishing from outbox | Infrastructure complexity (Kafka Connect cluster) | Requires Kafka Connect setup |
| Schema Registry (Confluent) | Event schema management & validation | Avro/Protobuf/JSON Schema registry, compatibility checks | Enforced schema evolution | Prevents breaking changes | Additional infrastructure | Not needed with JSON + versioned types |
| OpenTelemetry | Distributed tracing with correlation IDs | Trace propagation, span context, W3C TraceContext | End-to-end tracing across events | Industry standard | Requires agent/SDK integration | Orthogonal to event design |

### So sánh tính năng chi tiết

| Feature | Debezium CDC | Schema Registry | OpenTelemetry | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Event publishing reliability | ✅ | N/A | N/A | ✅ (outbox poller) | ⭐ Must |
| Schema versioning | N/A | ✅ | N/A | ✅ (type versioning) | ⭐ Must |
| Distributed tracing | N/A | N/A | ✅ | ✅ (correlation ID) | Nice to have |
| Low infrastructure overhead | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| PostgreSQL compatibility | ✅ | ✅ | ✅ | ✅ | ⭐ Must |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Transactional Outbox** | Save event to outbox table in same TX as domain change. Background poller publishes to Kafka | Exactly-once delivery guarantee; no event loss | Adds polling latency (50-500ms); requires cleanup job | Need reliable event delivery without CDC infrastructure | microservices.io |
| 2 | **CloudEvents Envelope** | Wrap domain events in CloudEvents standard envelope (id, source, type, time, specversion, data) | Industry standard; interoperable; self-describing events | Adds envelope overhead; team needs to learn spec | Multi-service ecosystem; cloud-native deployment | cloudevents.io |
| 3 | **Event Store Table** | Append-only PostgreSQL table storing all domain events with sequence numbers | Full audit trail; replay capability; temporal queries | Storage growth; need archival strategy | Need event replay / projection rebuild capability | Arkency blog |
| 4 | **Type-Based Versioning** | Include version in event type string: `UserRegisteredEvent.v2`. Upcaster transforms v1→v2 on read | Simple, explicit; backward compatible | Upcaster chain complexity grows over time | Schema will evolve; need to support old events | AxonIQ docs |
| 5 | **Idempotent Consumer** | Store processed event IDs in `processed_events` table. Check-before-process pattern | Prevents duplicate processing; safe retries | Adds a table + lookup per event | At-least-once delivery (Kafka default) | Confluent |
| 6 | **Topic-Per-Aggregate** | All events for an aggregate type on one Kafka topic, partitioned by aggregate ID | Ordering guarantee within aggregate; simpler consumer | Topic may carry many event types; needs type discriminator | Need ordered lifecycle events (register → update → delete) | Confluent blog |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | How does `eventsourcing-utils` internal event store work? | ✅ | Internal library — not publicly documented; need to read source | Medium — determines if event store already exists |
| 2 | Should `account-service` ProfileKafkaListener consume `UserRegisteredEvent` directly or a dedicated integration event? | ✅ | Architecture decision pending — both approaches valid | Medium — affects event payload design |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-08-21
> **Next step**: Comparison Analysis (comparison_analysis.md)
