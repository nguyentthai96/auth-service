# Research Handoff: User Registration Event

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | User Registration Event |
| **Ngày hoàn thành** | 2025-08-22 |
| **Recommendation** | Build from scratch (enhance existing) |
| **Research directory** | `openspec/research/user-registration-event/` |
| **Status** | complete |

---

## 1. Recommendation

**Build from scratch (enhance existing)** — the auth-service already has production-grade event sourcing infrastructure (EventService, EventEnvelope, OutboxPoller, KafkaEventPublisher) and an existing `UserRegisteredEvent` domain event with 11 enriched fields. The only missing pieces are: (1) `RegistrationEventRecorder` helper service for fire-and-forget event recording, (2) `UserRegistrationFailedEvent` domain event for security monitoring, and (3) `RegistrationFailureReason` enum for failure classification. All follow established internal patterns (LoginEventRecorder, UserLoginFailedEvent, LoginFailureReason). Estimated effort: 2-3 developer-days.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | Keycloak (8.75/10) — best event type taxonomy reference (EventType.REGISTER, REGISTER_ERROR with details map). Verdict: reference only, not directly integrable. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | CloudEvents 1.0, OCSF Account Change Activity (class_uid=3001), and Auth0 Post User Registration Action provide richest field references. 8 sources analyzed across 3 iterations. | [web_research.md](./web_research.md) |
| Gap Coverage | 100% coverage — all 5 identified gaps addressed in technical spec (RegistrationEventRecorder helper, UserRegistrationFailedEvent, consumer documentation, schema versioning strategy, fire-and-forget resilience). | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | Existing infrastructure complete: EventService, EventEnvelope, OutboxPoller, LoginEventRecorder (pattern template). Existing UserRegisteredEvent has 11 enriched fields. Gap: RegisterHandler calls EventService.record() directly without fire-and-forget protection. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Record Successful Registration Event | Record UserRegisteredEvent with enriched context to event store + outbox via RegistrationEventRecorder (fire-and-forget) | Must |
| UC-002 | Record Failed Registration Event | Record UserRegistrationFailedEvent with RegistrationFailureReason classification on registration failure | Must |
| UC-003 | Query Registration Events from Event Store | Admin queries registration events via existing GET /api/internal/events/User/{id} | Should |
| UC-004 | Consume Registration Events from Kafka | Downstream services consume from iam.user.registered / iam.user.registration_failed topics | Should |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Follow existing EventService + helper service (RegistrationEventRecorder) + domain event pattern. Fire-and-forget wrapper around EventService.record(). |
| Data model | No new database tables. 1 new domain event class: UserRegistrationFailedEvent (7 fields). 1 new enum: RegistrationFailureReason (6 values). 1 new helper: RegistrationEventRecorder. Existing UserRegisteredEvent unchanged. |
| APIs | 0 new endpoints. 1 new Kafka topic: iam.user.registration_failed. Existing topic iam.user.registered unchanged. |
| Key dependencies | None new — uses existing EventService, EventStorePort, OutboxPort, DomainEvent interface |
| Risk areas | 1) Event recording must not block registration (mitigated: fire-and-forget pattern via RegistrationEventRecorder). 2) Schema evolution for UserRegisteredEvent (mitigated: documented backward-compatible change policy). |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec user-registration-event --from-research` | Muốn explore thêm, có nhiều hướng tiếp cận |
| URD Analysis | `/wf_pre_openspec openspec/research/user-registration-event/business_analysis.md` | Đã rõ requirements, muốn formalize |
| OpenSpec (direct) | `/wf_openspec user-registration-event` | Đã rõ mọi thứ, muốn generate artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis (12 related features, 3 code patterns, tech stack) |
| [opensource_findings.md](./opensource_findings.md) | 2 | Open source evaluation: 4 projects scored (Keycloak 8.75, Axon 8.10, Spring Modulith 7.30, Eventuate 6.10) |
| [web_research.md](./web_research.md) | 3 | Internet research: 8 sources across 3 iterations, 3 products evaluated, 5 approaches documented |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Comparison matrix (10 features), gap analysis (5 gaps), decision matrix (Custom Build: 8.60 vs Keycloak: 4.55) |
| [business_analysis.md](./business_analysis.md) | 5 | Business analysis: 4 use cases, 12 business rules, 6 NFRs, glossary |
| [technical_spec.md](./technical_spec.md) | 6 | Technical specification: 3 new classes, 1 class modification, 10 test cases, downstream consumer documentation |
| [validation_report.md](./validation_report.md) | 7 | Quality review: all 5 checks PASS, 0 issues |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 8 web sources and 4 GitHub repos have valid URLs |
| Consistency | ✅ | BA ↔ Tech Spec aligned — UC-ID mapping, entity fields, event types consistent |
| Completeness | ✅ | All UCs have flows, all entities have field definitions, all APIs have payload examples |
| Feasibility | ✅ | Feasible with current stack — follows LoginEventRecorder/UserLoginFailedEvent patterns exactly |
| Gap Coverage | ✅ | All 5 gaps from comparison_analysis addressed in technical spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
