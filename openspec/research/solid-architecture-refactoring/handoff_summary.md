---
type: research_handoff
feature: SOLID Architecture Refactoring
date: 2026-09-22
recommendation: build
research_dir: openspec/research/solid-architecture-refactoring/
status: complete
---

# Research Handoff: SOLID Architecture Refactoring

## Recommendation

**BUILD** — Tự implement lightweight design patterns (CommandBus, Authentication Pipeline, MFA Provider Strategy, Enum Operator, Application Use Cases). Không adopt framework bên ngoài nặng (Axon, OPA) vì auth-service cần full control và các pattern cần thiết đều lightweight.

## Key Findings

- **Open Source**: Tham khảo Keycloak SPI (Auth Flow + MFA Provider), Axon (CommandBus), MediatR (Mediator interface), OPA (operator extensibility). Không adopt trực tiếp — tự build lightweight.
- **Web Research**: Chain of Responsibility + Mediator + Strategy là 3 patterns chính cần áp dụng. Đã có evidence từ Spring Security FilterChain, Keycloak SPI, và enterprise CQRS implementations.
- **Gap Coverage**: 10/10 gaps identified, 9/10 fully addressed, 1 deferred (ResponseBodyAdvice)

## Use Cases Identified

| UC | Description | Priority |
|----|-------------|----------|
| UC-001 | Authentication Pipeline — tách LoginHandler God Class (17 deps → 2) | P1 |
| UC-002 | CommandBus/Mediator — giảm CqrsAuthController deps (13 → 5) | P2 |
| UC-003 | MFA Provider Strategy — thay when-clause bằng polymorphic dispatch | P2 |
| UC-004 | RBAC/PBAC Application Layer — giải quyết DIP violations | P1 |
| UC-005 | CaptchaController OCP fix — dùng existing registry | P1 (Quick Win) |
| UC-006 | PolicyEvaluator Enum Strategy — thay when bằng enum | P1 (Quick Win) |

## Implementation Phases

| Phase | Effort | Risk |
|-------|--------|------|
| P1a: Quick Wins (Captcha + PolicyEvaluator) | 2-4h | 🟢 Low |
| P1b: RBAC/PBAC Application Layer | 8-12h | 🟡 Medium |
| P1c: Split RbacControllers.kt | 1-2h | 🟢 Low |
| P2a: CommandBus infrastructure | 4-6h | 🟡 Medium |
| P2b: Authentication Pipeline | 12-16h | 🔴 High |
| P2c: MFA Provider Strategy | 6-8h | 🟡 Medium |
| P3: ResponseBodyAdvice (DEFERRED) | 4h | 🟢 Low |

**Total**: 37-52h (excluding P3)

## Ready for

- `/wf_brainstorm_openspec solid-architecture-refactoring --from-research` — deep thinking với research context
- `/wf_pre_openspec openspec/research/solid-architecture-refactoring/business_analysis.md` — formal URD analysis
- `/wf_openspec_apply` — trực tiếp implement (nếu user approve plan)

## Research Artifacts

| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| [opensource_findings.md](./opensource_findings.md) | Open source evaluation (5 projects scored) |
| [web_research.md](./web_research.md) | Internet research (3 iterations, 7 findings) |
| [comparison_analysis.md](./comparison_analysis.md) | Comparison matrix + gap analysis |
| [business_analysis.md](./business_analysis.md) | Business analysis (6 use cases, 15 BRs) |
| [technical_spec.md](./technical_spec.md) | Technical spec (architecture, code, sequence diagrams) |
| [validation_report.md](./validation_report.md) | Quality review (5/5 categories PASS/WARN) |
