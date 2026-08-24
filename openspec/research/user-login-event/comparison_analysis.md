# Phân tích so sánh: User Login Event

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Login Event |
| **Ngày phân tích** | 2025-08-22 |
| **Recommendation** | **Build from scratch following existing EventService patterns** |
| **Rationale** | The auth-service already has a production-grade event sourcing infrastructure (EventService, EventEnvelope, OutboxPoller, Kafka relay). The gap is specifically two domain event classes + one helper service + LoginHandler integration. No external library or framework adoption is needed. |
| **Confidence** | **HIGH** — existing codebase already demonstrates the exact pattern (TokenIssuedEvent, UserRegisteredEvent). Implementation is a direct extension. |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Keycloak Events SPI | Open Source (reference) | SPI-based event listeners with EventType enum and Event model | Battle-tested model, rich field set | Java SPI architecture, not directly integrable | ⚠️ Reference only | 8.45 |
| 2 | Axon Framework | Open Source (framework) | Full CQRS/ES framework with event store and Kafka extension | Complete ES infrastructure | Heavy dependency, replaces our EventService | ❌ Overkill | 7.60 |
| 3 | OCSF Schema Adoption | Standard (reference) | Adopt OCSF Authentication event class field naming | Industry standard, SIEM-compatible | Schema-only, no implementation; field names don't match our conventions | ⚠️ Reference only | 7.0 |
| 4 | Custom Build | In-house | Two domain events + LoginEventRecorder + LoginHandler integration | Follows existing patterns perfectly, minimal code addition, full control | Must maintain ourselves | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Keycloak | Axon Framework | OCSF Schema | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Login success event | ✅ | ❓ (user-defined) | ✅ (schema) | ✅ | ⭐ Must |
| Login failure event | ✅ | ❓ (user-defined) | ✅ (schema) | ✅ | ⭐ Must |
| Enriched payload (device, IP, MFA) | ⚠️ (partial) | ❓ | ✅ (schema) | ✅ | ⭐ Must |
| Event store persistence | ⚠️ (custom table) | ✅ | ❌ | ✅ (EventStorePort) | ⭐ Must |
| Transactional outbox | ❌ | ✅ | ❌ | ✅ (OutboxPort) | ⭐ Must |
| Kafka publishing | ❌ (SPI needed) | ✅ | ❌ | ✅ (OutboxPoller) | ⭐ Must |
| CloudEvents envelope | ❌ | ❌ | ⚠️ (compatible) | ✅ (EventEnvelope) | ⭐ Must |
| Failure reason classification | ✅ | ❓ | ✅ | ✅ | ⭐ Must |
| Correlation ID | ❌ | ✅ | ❌ | ✅ | ⭐ Must |
| Schema versioning | ❌ | ✅ | ✅ | ✅ | Nice to have |
| Session promotion context | ❌ | ❌ | ❌ | ✅ | Nice to have |
| Compatible with existing EventService | ❌ | ❌ | N/A | ✅ | ⭐ Must |
| **Coverage** | **5/12** | **5/12** | **4/12** | **12/12** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 9 | Custom Build: 100% / Others: 33-56% |
| Nice to have | 3 | Custom Build: 100% / Others: 0-67% |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Keycloak | Axon | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Login success event with enriched payload | UC-001 | ⚠️ | ❓ | ✅ | Partial — Keycloak lacks device fingerprint, MFA status |
| Login failure event with reason classification | UC-002 | ✅ | ❓ | ✅ | No — Keycloak has LOGIN_ERROR |
| Event store persistence (append-only) | FR-004 | ⚠️ | ✅ | ✅ | Partial — Keycloak custom table format |
| Transactional outbox for Kafka relay | FR-007 | ❌ | ✅ | ✅ | Yes — Keycloak lacks outbox |
| CloudEvents envelope with correlation ID | FR-006 | ❌ | ❌ | ✅ | Yes — both lack CloudEvents |
| Compatible with existing EventService API | FR-INT | ❌ | ❌ | ✅ | Yes — external solutions don't integrate |
| Helper service pattern (fail-safe recording) | DD-003 | N/A | N/A | ✅ | N/A — project-specific |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Login success event | No event store record; only LoginSessionEntity + TokenIssuedEvent | `UserLoggedInEvent` with enriched payload → event store + outbox + Kafka | Missing domain event class + recording integration | HIGH |
| Login failure event | No structured event; only AuditLogService.logEvent(LOGIN_FAILED) + rate limit counters | `UserLoginFailedEvent` with failure reason → event store + outbox + Kafka | Missing domain event class + recording integration | HIGH |
| Login event recording | No dedicated recorder | `LoginEventRecorder` helper service mirroring TokenEventRecorder | Missing helper service | MEDIUM |
| LoginHandler integration | Handler does not record domain events for login action | Handler calls LoginEventRecorder on success and failure | Code change in LoginHandler | MEDIUM |
| Kafka topics | No login-specific topics | `iam.user.logged_in` + `iam.user.login_failed` | Topic creation (auto-create on first publish) | LOW |
| Correlation ID for login | Not threaded from HTTP request to event | CqrsAuthController extracts X-Correlation-ID → LoginCommand → event | LoginCommand field addition, controller change | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse (Keycloak patterns) | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 2-3 developer-days | N/A (cannot reuse code directly) | Custom Build |
| Maintenance burden | Low — 2 data classes + 1 service + handler integration | N/A | Custom Build |
| Feature coverage | 100% of requirements | 56% at best | Custom Build |
| Integration effort | Minimal — follows existing patterns exactly | High — would require adapting foreign patterns | Custom Build |
| Long-term flexibility | Full control, can evolve with system | Locked to external patterns | Custom Build |
| Risk | Very low — patterns already proven in codebase | N/A | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Keycloak (ref) | Axon (framework) | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 5 | 5 | 10 |
| Integration ease | 25% | 2 | 2 | 10 |
| Maintenance | 20% | N/A (ref only) | 3 | 9 |
| Community/Support | 15% | 10 | 7 | 5 |
| Learning curve | 10% | 3 | 4 | 10 |
| **Tổng điểm (weighted)** | | **4.55** | **3.80** | **9.20** |

### Reasoning

**Recommended approach**: Build from scratch

**Lý do**:
1. **Existing infrastructure is complete** — EventService, EventEnvelope, OutboxPoller, KafkaEventPublisher are all production-ready. The only missing piece is the domain event classes and handler integration.
2. **Proven pattern exists** — `UserRegisteredEvent` + `RegisterHandler` and `TokenIssuedEvent` + `TokenEventRecorder` demonstrate the exact pattern to follow. Implementation is essentially copy-adapt.
3. **External solutions add complexity without value** — Keycloak's event model is Java SPI-based (incompatible), Axon replaces our entire EventService (overkill), OCSF is schema-only (no code).

**Trade-offs chấp nhận**:
- No OCSF schema compliance — chấp nhận vì OCSF field names don't match our Kotlin conventions. SIEM mapping can be done at consumer/ETL level.
- No GeoIP lookup implementation — chấp nhận vì this is orthogonal to the event design. LoginSessionEntity already has `geoCountry` field populated as placeholder.

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Event recording failure blocks login flow | LOW | HIGH | LoginEventRecorder catches all exceptions (fire-and-forget), matching TokenEventRecorder pattern |
| Event payload too large for Kafka message | LOW | MEDIUM | Use concise field names, exclude binary data. Estimated payload ~500 bytes — well within Kafka default 1MB |
| Duplicate events between UserLoggedInEvent and TokenIssuedEvent | MEDIUM | LOW | Clear separation: UserLoggedInEvent = auth context (who, where, how), TokenIssuedEvent = token context (JTI, roles, expiry). Document in event class Javadoc. |
| Schema evolution breaks downstream consumers | LOW | MEDIUM | Use `schemaVersion` in EventEnvelope (already implemented). Add fields as optional with defaults. |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build | 2-3 days | LOW | LOW |
| Keycloak integration | N/A (reference only) | N/A | N/A |
| Axon Framework adoption | 10-15 days (migration) | HIGH | HIGH |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
