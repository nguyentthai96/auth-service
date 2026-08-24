# Research Handoff: User Login Event

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | User Login Event |
| **Ngày hoàn thành** | 2025-08-22 |
| **Recommendation** | Build from scratch |
| **Research directory** | `openspec/research/user-login-event/` |
| **Status** | complete |

---

## 1. Recommendation

**Build from scratch** — the auth-service already has a production-grade event sourcing infrastructure (EventService, EventEnvelope, OutboxPoller, KafkaEventPublisher). The only missing pieces are two domain event classes (`UserLoggedInEvent`, `UserLoginFailedEvent`), one helper service (`LoginEventRecorder`), and LoginHandler integration. No external library adoption needed. Estimated effort: 2-3 developer-days.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | Keycloak (8.45/10) — best event model reference (EventType enum, Event class with details map). Verdict: reference only, not directly integrable. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | OCSF Authentication Event schema (class_uid=3002) and Microsoft Entra sign-in logs provide the richest field references. 8 sources analyzed. | [web_research.md](./web_research.md) |
| Gap Coverage | 100% coverage — all 7 identified gaps addressed in technical spec (event store recording, Kafka publishing, enriched payload, correlation ID, schema versioning, failure classification, fire-and-forget resilience). | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | Existing infrastructure complete: EventService, EventEnvelope, OutboxPoller, TokenEventRecorder (pattern template). Gap: LoginHandler does not record domain events for login actions. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Record Successful Login Event | Record UserLoggedInEvent with enriched auth context to event store + outbox on login success | Must |
| UC-002 | Record Failed Login Event | Record UserLoginFailedEvent with failure reason classification on login failure | Must |
| UC-003 | Query Login Events from Event Store | Admin queries login events via existing GET /api/internal/events/User/{id} | Should |
| UC-004 | Consume Login Events from Kafka | Downstream services consume from iam.user.logged_in / iam.user.login_failed topics | Should |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Follow existing EventService + helper service (LoginEventRecorder) + domain event pattern |
| Data model | No new database tables. 2 domain event classes: UserLoggedInEvent (14 fields), UserLoginFailedEvent (7 fields). 1 enum: LoginFailureReason (9 values) |
| APIs | 0 new endpoints. 2 new Kafka topics: iam.user.logged_in, iam.user.login_failed |
| Key dependencies | None new — uses existing EventService, EventStorePort, OutboxPort, DomainEvent interface |
| Risk areas | 1) Event recording must not block login (mitigated: fire-and-forget pattern). 2) Distinction from TokenIssuedEvent (mitigated: clear responsibility split documented) |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec user-login-event --from-research` | Muốn explore thêm patterns cho event recording |
| URD Analysis | `/wf_pre_openspec openspec/research/user-login-event/business_analysis.md` | Đã rõ requirements, muốn formalize URD |
| OpenSpec (direct) | `/wf_openspec user-login-event` | Đã rõ mọi thứ, muốn generate design.md + tasks.md ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis (LoginHandler, EventService, existing events) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 4 projects evaluated: Keycloak (8.45), Axon (7.60), ORY Kratos (7.30), Spring Auth Server (7.25) |
| [web_research.md](./web_research.md) | 3 | 3 iterations, 8 sources: OCSF, Microsoft Entra, Auth0, CloudEvents, AWS CloudTrail, Keycloak docs |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Feature matrix (Custom Build: 12/12 coverage), decision matrix (Custom: 9.20 vs Keycloak ref: 4.55) |
| [business_analysis.md](./business_analysis.md) | 5 | 4 use cases, 13 business rules, 6 functional requirements, 6 non-functional requirements |
| [technical_spec.md](./technical_spec.md) | 6 | 4 new classes, LoginHandler integration strategy, 15 test cases, sequence diagrams |
| [validation_report.md](./validation_report.md) | 7 | All 5 checks PASS on first iteration |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 8 sources verified — stable documentation URLs |
| Consistency | ✅ | BA ↔ Tech Spec aligned (UCs → classes, exceptions → enum values) |
| Completeness | ✅ | All UCs have flows, all event fields specified, 15 test cases defined |
| Feasibility | ✅ | Feasible with current stack — follows proven TokenEventRecorder pattern |
| Gap Coverage | ✅ | All 7 gaps from comparison_analysis addressed in tech spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
