# Phân tích so sánh: User Registration Event

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Registration Event (Event Sourcing + CQRS) |
| **Ngày phân tích** | 2025-08-21 |
| **Recommendation** | **Build from scratch (inspired by Axon patterns + CloudEvents envelope + Transactional Outbox)** |
| **Rationale** | Project already has `eventsourcing-utils` CQRS infrastructure. Enriching existing `UserRegisteredEvent` with CloudEvents-inspired envelope, adding a PostgreSQL event store table, and implementing transactional outbox is more aligned than adopting an external framework |
| **Confidence** | **HIGH** — codebase analysis confirms existing CQRS patterns; incremental enhancement is lowest risk |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Axon Framework | Open Source | Full ES+CQRS framework with event store, sagas, upcasters | Complete solution, proven at scale | Heavy adoption cost; replaces existing eventsourcing-utils | ⚠️ | 8.60 |
| 2 | Spring Modulith Events | Open Source | Transactional outbox via event publication log | Native Spring integration; reliable event publishing | Not full ES; no event versioning/upcasters | ⚠️ | 8.05 |
| 3 | CloudEvents SDK | Open Source | Standard event envelope format + Kafka binding | Industry standard; lightweight; interoperable | Only envelope — no store, no CQRS | ⚠️ | 7.45 |
| 4 | Custom Build | In-house | Enrich existing events + PostgreSQL event store + outbox + CloudEvents envelope | Full control; no framework lock-in; incremental | Development effort; no community support | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Axon Framework | Spring Modulith | CloudEvents SDK | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Rich UserRegisteredEvent payload | ✅ | ❌ | ❌ | ✅ | ⭐ Must |
| Event Store (append-only persistence) | ✅ | ⚠️ (publication log) | ❌ | ✅ | ⭐ Must |
| Event Schema Versioning | ✅ (upcasters) | ❌ | ⚠️ (dataschema) | ✅ | ⭐ Must |
| Transactional Outbox | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| Kafka Integration | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Standard Event Envelope (CloudEvents) | ❌ | ❌ | ✅ | ✅ | Nice to have |
| Idempotent Consumer Support | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| Aggregate Root Pattern | ✅ | ❌ | ❌ | ❓ | Optional |
| Saga/Process Manager | ✅ | ❌ | ❌ | ❓ | Optional |
| Compatibility with eventsourcing-utils | ❌ | ✅ | ✅ | ✅ | ⭐ Must |
| Minimal Infrastructure Change | ❌ | ✅ | ✅ | ✅ | ⭐ Must |
| **Coverage** | **7/11** | **4/11** | **3/11** | **9/11** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 7 | 43% (CloudEvents) - 100% (Custom) |
| Nice to have | 1 | 0% (Axon/Modulith) - 100% (Custom/CloudEvents) |
| Optional | 3 | 0% (CloudEvents/Modulith) - 100% (Axon) |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Axon | Spring Modulith | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Enriched event payload | Current system lacks email, IP, roles | ✅ | ❌ | ✅ | Có — Axon/Modulith don't solve payload design |
| Event versioning | Schema evolution needed as system grows | ✅ | ❌ | ✅ | Có — only Axon + Custom handle this |
| PostgreSQL event store | Audit trail + replay requirement | ✅ | ⚠️ | ✅ | Partial — Spring Modulith only has publication log |
| Reliable Kafka publishing | Current is fire-and-forget | ✅ | ✅ | ✅ | No — all solutions solve this |
| Deduplication/idempotency | At-least-once Kafka delivery | ⚠️ | ❌ | ✅ | Có — only Custom explicitly handles |
| Correlation ID | Distributed tracing across services | ⚠️ | ❌ | ✅ | Có — only Custom explicitly designs |
| Compatible with existing codebase | eventsourcing-utils Command/Handler | ❌ | ✅ | ✅ | Có — Axon is incompatible |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Event Payload | Minimal (userId, username, domainCode) | Rich (+ email, fullName, phone, ipAddress, userAgent, roles, correlationId) | Missing 7+ fields | HIGH |
| Event Publishing | Fire-and-forget (KafkaEventPublisher) | Transactional outbox (save event + domain change in same TX) | No delivery guarantee | HIGH |
| Event Persistence | No event store (events lost after Kafka retention) | PostgreSQL event_store table (append-only, immutable) | No replay/audit trail | HIGH |
| Event Naming | Duplicate definitions (port vs command), inconsistent naming | Single definition with CloudEvents envelope + versioned type | Duplication + inconsistency | MEDIUM |
| Consumer Idempotency | No deduplication mechanism | event_id-based deduplication table for consumers | Potential duplicate processing | MEDIUM |
| Schema Versioning | No versioning strategy | Type-based versioning with upcaster support | Cannot evolve schema safely | MEDIUM |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Axon Framework | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 3-5 developer-days | 5-10 days (learning curve + migration) | Custom Build |
| Maintenance burden | Low (own code, simple patterns) | Medium (framework updates, API changes) | Custom Build |
| Feature coverage | 100% for Must-have features | 100% + extra (sagas, snapshots) | Tie |
| Integration effort | Low (extend existing code) | High (replace eventsourcing-utils) | Custom Build |
| Long-term flexibility | High (full control) | Medium (bound to Axon decisions) | Custom Build |
| Risk | Low (incremental change) | Medium (framework migration risk) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Axon Framework | Spring Modulith | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 9 | 5 | 9 |
| Integration ease | 25% | 3 | 7 | 9 |
| Maintenance | 20% | 6 | 8 | 7 |
| Community/Support | 15% | 9 | 8 | 3 |
| Learning curve | 10% | 4 | 7 | 8 |
| **Tổng điểm (weighted)** | | **6.25** | **6.65** | **7.80** |

### Reasoning

**Recommended approach**: Build from scratch (incremental enhancement of existing codebase)

**Lý do**:
1. **Existing CQRS infrastructure**: The project already has `eventsourcing-utils` with Command/CommandHandler, RegisterHandler, and KafkaEventPublisher. Building on this foundation is faster and lower risk than adopting an external framework.
2. **Specific requirements**: The feature needs enriched event payload, PostgreSQL event store, transactional outbox, and idempotent consumers — all of which are well-understood patterns that can be implemented in 3-5 developer-days without framework overhead.
3. **Infrastructure alignment**: PostgreSQL and Kafka are already in the stack. Adding an event store table and outbox table requires only Flyway migrations — no new infrastructure. External solutions (Axon Server, EventStoreDB) would add operational complexity.

**Trade-offs chấp nhận**:
- No built-in saga/process manager — chấp nhận vì saga is not needed for user registration flow (downstream consumers are independent)
- No built-in snapshots — chấp nhận vì user aggregate has simple state; snapshot optimization is premature
- No community support for custom event store — chấp nhận vì the patterns are well-documented and simple

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Event store table grows unbounded | MED | LOW | Implement archival strategy (move events older than 90 days to archive table) |
| Outbox poller adds latency | LOW | LOW | Configure 100ms poll interval; upgrade to CDC (Debezium) if needed later |
| Schema versioning complexity | LOW | MED | Start with v1; upcaster framework only needed when v2 is introduced |
| Duplicate event definitions | HIGH | MED | Resolve in this feature — consolidate to single `UserRegisteredEvent` in port package |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build | 3-5 days | LOW | LOW |
| Axon Framework | 8-12 days | HIGH | MED |
| Spring Modulith (partial) | 2-3 days (outbox only) | LOW | LOW |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
