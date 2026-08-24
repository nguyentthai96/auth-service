# Phân tích so sánh: User Registration Event

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Registration Event |
| **Ngày phân tích** | 2025-08-22 |
| **Recommendation** | **Build from scratch (enhance existing)** |
| **Rationale** | Auth-service already has production-grade UserRegisteredEvent + EventService + outbox infrastructure. Enhancement needed: RegistrationEventRecorder helper, UserRegistrationFailedEvent for monitoring, and schema versioning documentation. No external adoption necessary. |
| **Confidence** | HIGH — existing codebase has proven patterns (LoginEventRecorder, TokenEventRecorder) that directly apply |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Keycloak Event SPI | Open Source | SPI-based listener with EventType enum, details map | Battle-tested taxonomy, REGISTER_ERROR event | Synchronous listeners, no event store | ⚠️ | 8.75 |
| 2 | Axon Framework | Open Source | Full CQRS/ES framework with @Aggregate, @Revision, event upcasting | Best-in-class event versioning | Requires full framework buy-in | ❌ | 8.10 |
| 3 | Spring Modulith | Open Source | ApplicationEvent externalization, event publication log | Spring-native, easy setup | Less control, lighter than outbox | ⚠️ | 7.30 |
| 4 | Eventuate Tram | Open Source | Transactional outbox via MESSAGE table, CDC relay | Strong outbox pattern | Smaller community, auth-service already has this | ⚠️ | 6.10 |
| 5 | Custom Build (enhance existing) | In-house | Enhance existing UserRegisteredEvent + add RegistrationEventRecorder + UserRegistrationFailedEvent | Perfectly fits existing architecture, no new dependencies | Dev effort needed, no external community | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Keycloak | Axon | Spring Modulith | Eventuate | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| UserRegisteredEvent (enriched) | ✅ | ✅ | ✅ | ✅ | ✅ (existing) | ⭐ Must |
| UserRegistrationFailedEvent | ✅ | ❓ | ❌ | ❌ | ❓ (to build) | ⭐ Must |
| Event Store Persistence | ❌ | ✅ | ⚠️ | ⚠️ | ✅ (existing) | ⭐ Must |
| Transactional Outbox | ❌ | ⚠️ | ⚠️ | ✅ | ✅ (existing) | ⭐ Must |
| Kafka Publishing | ⚠️ | ✅ | ✅ | ✅ | ✅ (existing) | ⭐ Must |
| CloudEvents Envelope | ❌ | ❌ | ❌ | ❌ | ✅ (existing) | ⭐ Must |
| Correlation ID | ❌ | ✅ | ❌ | ✅ | ✅ (existing) | ⭐ Must |
| Fire-and-Forget Helper | ❌ | ✅ | ❌ | ❌ | ❓ (to decide) | Nice to have |
| Schema Versioning | ❌ | ✅ | ❌ | ❌ | ⚠️ (basic) | Nice to have |
| Failure Reason Enum | ✅ | ❌ | ❌ | ❌ | ❓ (to build) | Nice to have |
| **Coverage** | **4/10** | **5/10** | **3/10** | **4/10** | **7/10** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 7 | 29%-100% (Keycloak 29% — Custom Build 100%) |
| Nice to have | 3 | 0%-67% (Axon 67% — Custom Build 33%) |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Keycloak | Axon | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Enriched registration success event | UC-001 | ✅ | ✅ | ✅ (existing) | None |
| Registration failure event with classification | UC-002 | ✅ | ❌ | ❓ (to build) | Custom Build — needs implementation |
| Fire-and-forget event recording | Pattern consistency | ❌ | ✅ | ❓ (to decide) | Design decision needed |
| Event store + outbox atomic persistence | FR-004, FR-007 | ❌ | ✅ | ✅ (existing) | None |
| Kafka topic for downstream consumers | UC-004 | ⚠️ | ✅ | ✅ (existing) | None |
| Schema versioning documentation | NFR | ❌ | ✅ | ⚠️ (basic) | Partial — needs strategy doc |
| Consumer documentation | UC-003, UC-004 | ❌ | ❌ | ❌ | All — no consumer docs anywhere |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| UserRegisteredEvent | ✅ Exists with 11 enriched fields in `auth.domain.event` | Same — already production-grade | None | LOW |
| Event recording in RegisterHandler | Direct `EventService.record()` in @Transactional (no try/catch) | RegistrationEventRecorder with fire-and-forget option | Design decision: transactional vs fire-and-forget | MEDIUM |
| Registration failure event | ❌ No structured failure event | UserRegistrationFailedEvent with RegistrationFailureReason enum | Missing implementation | MEDIUM |
| Schema versioning | Basic `schemaVersion=1` in EventEnvelope | Documented evolution strategy, @Revision equivalent | Missing documentation | LOW |
| Consumer documentation | ❌ No consumer docs | Documented downstream consumer list and expected reactions | Missing documentation | LOW |
| RegistrationEventRecorder | ❌ Not implemented — direct EventService call | Dedicated helper following LoginEventRecorder pattern | Missing implementation | MEDIUM |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Axon Framework | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 2-3 developer-days | 2-3 weeks (migration) | Custom Build |
| Maintenance burden | Low — follows existing patterns | Medium — framework dependency updates | Custom Build |
| Feature coverage | 100% of Must requirements | 70% (missing custom outbox integration) | Custom Build |
| Integration effort | Minimal — same codebase patterns | Major — refactor to Axon annotations | Custom Build |
| Long-term flexibility | Full control over event model | Locked to Axon conventions | Custom Build |
| Risk | Low — proven patterns | High — framework migration risk | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Keycloak | Axon | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 4/10 | 5/10 | 9/10 |
| Integration ease | 25% | 3/10 | 2/10 | 10/10 |
| Maintenance | 20% | 3/10 | 5/10 | 9/10 |
| Community/Support | 15% | 10/10 | 8/10 | 3/10 |
| Learning curve | 10% | 5/10 | 4/10 | 10/10 |
| **Tổng điểm (weighted)** | | **4.55** | **4.50** | **8.60** |

### Reasoning

**Recommended approach**: Build from scratch (enhance existing implementation)

**Lý do**:
1. **Existing infrastructure is production-grade** — EventService, EventEnvelope, OutboxPoller, KafkaEventPublisher already handle all event sourcing plumbing. UserRegisteredEvent data class already exists with enriched payload (FR-001, FR-002).
2. **Proven internal patterns** — LoginEventRecorder and TokenEventRecorder demonstrate the exact helper service pattern needed for RegistrationEventRecorder. Copy-and-adapt takes ~1 hour vs weeks of framework adoption.
3. **No integration cost** — Custom build requires zero new dependencies, zero migration risk, zero learning curve. All developers already understand the EventService + helper recorder pattern.

**Trade-offs chấp nhận**:
- No built-in event upcasting framework (like Axon) — accepted because schemaVersion field + backward-compatible changes is sufficient for auth-service's scale
- No external community support for custom code — accepted because the pattern is simple (< 50 LOC per class) and well-tested internally

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Event recording failure blocks registration | LOW | HIGH | Implement RegistrationEventRecorder with fire-and-forget option |
| Schema evolution breaks consumers | LOW | MEDIUM | Document backward-compatible change policy, use schemaVersion field |
| Missing failure events for monitoring | MEDIUM | MEDIUM | Implement UserRegistrationFailedEvent with RegistrationFailureReason enum |
| Downstream consumer confusion (no docs) | LOW | LOW | Document consumer expectations in technical spec |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build (enhance existing) | 2-3 | LOW | LOW |
| Adopt Axon Framework | 15-20 | HIGH | MEDIUM |
| Adopt Spring Modulith | 5-7 | MEDIUM | LOW |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
