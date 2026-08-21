# Phân tích so sánh: User Login Event

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Login Event |
| **Ngày phân tích** | 2025-08-22 |
| **Recommendation** | **Build from scratch (extend existing EventService pattern)** |
| **Rationale** | Project already has mature ES infrastructure. UserLoggedInEvent is an incremental extension following the exact pattern established by UserRegisteredEvent. All evaluated alternatives require framework migration or add unnecessary complexity. |
| **Confidence** | **HIGH** — identical pattern already proven in production for UserRegisteredEvent |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Axon Framework | Open Source | Full ES+CQRS framework with built-in event modeling | Feature-complete, enterprise-grade | Requires full framework migration, heavy dependency | ❌ | 8.45 |
| 2 | Spring Modulith | Open Source | Event publication log + Kafka externalization | Spring-native, lightweight | No full event store, limited replay | ⚠️ | 7.60 |
| 3 | Keycloak Events | Open Source | Built-in login event system | Mature, extensive event types | No transactional outbox, separate event DB | ❌ | 5.50 |
| 4 | Custom Build (extend EventService) | In-house | New event class + EventService.record() in LoginHandler | Minimal code, follows proven pattern, full control | Must maintain custom code | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Axon Framework | Spring Modulith | Keycloak Events | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Enriched login event payload | ✅ | ⚠️ | ⚠️ | ✅ | ⭐ Must |
| Event store persistence | ✅ | ⚠️ | ⚠️ | ✅ | ⭐ Must |
| Transactional outbox | ✅ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Kafka publishing | ✅ | ✅ | ⚠️ | ✅ | ⭐ Must |
| CloudEvents envelope | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| Schema versioning | ✅ | ❌ | ❌ | ✅ | ⭐ Must |
| Correlation ID | ✅ | ❌ | ❌ | ✅ | ⭐ Must |
| CQRS command integration | ✅ | ❌ | ❌ | ✅ | ⭐ Must |
| Login failed event | ✅ | ❓ | ✅ | ✅ | ⭐ Must |
| eventsourcing-utils compatible | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Zero migration effort | ❌ | ⚠️ | ❌ | ✅ | ⭐ Must |
| **Coverage** | **7/11** | **3/11** | **3/11** | **11/11** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 11 | 27% - 100% |
| Nice to have | 0 | N/A |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Axon Framework | Spring Modulith | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| UserLoggedInEvent with full context | UC-001 | ✅ | ⚠️ | ✅ | Axon requires migration |
| UserLoginFailedEvent | UC-002 | ✅ | ❓ | ✅ | No gap for custom |
| EventService.record() integration | UC-001 | ❌ | ❌ | ✅ | Only custom supports existing API |
| Kafka topic iam.user.logged_in | UC-001 | ✅ | ✅ | ✅ | No gap |
| event_store table persistence | UC-001 | ✅ (own store) | ❌ | ✅ | Spring Modulith lacks ES |
| Outbox + OutboxPoller relay | UC-001 | ✅ (own poller) | ⚠️ | ✅ | Only custom uses existing infra |
| Correlation ID propagation | UC-001 | ✅ | ❌ | ✅ | No gap for custom |
| LoginCommand enrichment | UC-001 | ❌ | ❌ | ✅ | Only custom touches LoginCommand |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Login event payload | `UserLoggedInEvent(userId, domainCode)` — 2 fields | Enriched event with 15+ fields (IP, device, MFA, session) | **Large** — event needs complete redesign | HIGH |
| Login event persistence | Not persisted (in-process SpringEvent only) | Persisted to event_store + outbox | **Large** — no EventService call in LoginHandler | HIGH |
| Login event Kafka publishing | Not published to Kafka | Published via outbox → OutboxPoller → Kafka | **Large** — no outbox entry for login events | HIGH |
| Failed login events | Not captured as domain event | `UserLoginFailedEvent` with failure reason and context | **New** — entirely new event type | MEDIUM |
| Correlation ID | Not present in login flow | Correlation ID in LoginCommand and events | **Medium** — LoginCommand needs new field | MEDIUM |
| Login event topic | N/A | `iam.user.logged_in` and `iam.user.login_failed` | **New** — new Kafka topics | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Axon Framework | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 2-3 days | 2-3 weeks (migration) | Custom Build |
| Maintenance burden | Low (follows existing pattern) | Medium (framework upgrades) | Custom Build |
| Feature coverage | 100% (all Must features) | 64% (compatible features) | Custom Build |
| Integration effort | Minimal (extend existing) | Significant (replace ES infrastructure) | Custom Build |
| Long-term flexibility | High (full control) | Medium (framework constraints) | Custom Build |
| Risk | Low (proven pattern) | Medium (migration risks) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Axon Framework | Spring Modulith | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 7/10 | 3/10 | 10/10 |
| Integration ease | 25% | 2/10 | 5/10 | 10/10 |
| Maintenance | 20% | 6/10 | 7/10 | 8/10 |
| Community/Support | 15% | 9/10 | 8/10 | 5/10 |
| Learning curve | 10% | 3/10 | 6/10 | 9/10 |
| **Tổng điểm (weighted)** | | **5.35** | **5.15** | **8.85** |

### Reasoning

**Recommended approach**: Build from scratch (extend existing EventService pattern)

**Lý do**:
1. **Proven pattern exists** — `RegisterHandler` + `EventService.record()` + `UserRegisteredEvent` already demonstrates the exact implementation pattern. LoginHandler needs the same 5-line integration.
2. **Zero migration risk** — All existing infrastructure (EventService, EventStorePort, OutboxPort, OutboxPoller, KafkaConfig) is reused as-is. Only new code: event class + handler modification.
3. **Full feature coverage** — Custom build delivers 100% of Must requirements including eventsourcing-utils compatibility, existing EventEnvelope integration, and consistent event naming.

**Trade-offs chấp nhận**:
- No community support for custom ES infrastructure — accepted because the infrastructure is already proven and stable
- Must maintain custom event classes — accepted because they are simple data classes with no complex logic

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Event store performance under high login frequency | LOW | MEDIUM | Event store append is O(1); login frequency is orders of magnitude lower than read queries. OutboxPoller already handles batching. |
| Event schema evolution breaking downstream consumers | LOW | HIGH | EventEnvelope includes schemaVersion. Follow backward-compatible additive changes. Document breaking changes in event type version suffix. |
| LoginHandler transaction scope expanding | LOW | MEDIUM | EventService.record() joins caller's @Transactional boundary — proven safe in RegisterHandler. Event store + outbox inserts are lightweight (2 INSERTs). |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build (extend existing) | 2-3 | LOW | LOW |
| Axon Framework migration | 15-20 | HIGH | MEDIUM |
| Spring Modulith integration | 5-8 | MEDIUM | MEDIUM |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
