---
type: research_handoff
feature: cache-starter-migration-upgrade
date: 2026-08-21
recommendation: build
research_dir: openspec/research/cache-starter-migration-upgrade/
status: complete
---

# Research Handoff: Base Cache Starter Migration & Upgrade

## Recommendation
**BUILD on top of `base-cache-starter`** — extend existing internal library with Encryption Engine (NONE/FULL/PARTIAL modes) + Smart Serialization Engine. Migrate auth-service to use `@Cacheable` + base-cache-starter thay vì custom `AbstractTwoTierCache`.

## Key Findings
- **Open Source**: JetCache (Alibaba) và J2Cache được đánh giá — cả hai đều KHÔNG có encryption support. base-cache-starter internal đạt score 8.5/10, chỉ thiếu encryption + smart serialization.
- **Web Research**: 5 search iterations, 8+ unique sources (redis.io, Spring docs, community articles). AES-256-GCM là industry standard cho cache encryption at rest.
- **Gap Coverage**: 100% — tất cả gaps (encryption, partial field encrypt, data type serialization) được thiết kế trong technical spec.

## Use Cases Identified
- **UC-001**: Migrate Permission Cache sang @Cacheable — xóa ~300 LOC custom code
- **UC-002**: Cache Encryption FULL mode — encrypt toàn bộ value cho sensitive data (sessions, tokens)
- **UC-003**: Cache Encryption PARTIAL mode — encrypt chỉ @CacheEncrypt fields (PII: email, phone, SSN)
- **UC-004**: Smart Serialization — tự động chọn strategy tối ưu per data type (String, Number, Object, List, Set, Map...)
- **UC-005**: Cross-Instance Cache Invalidation — Redis Pub/Sub broadcast (đã có sẵn trong base-cache-starter)

## Ready for
- `/wf_brainstorm_openspec cache-starter-migration-upgrade --from-research` — deep thinking with research context
- `/wf_pre_openspec openspec/research/cache-starter-migration-upgrade/business_analysis.md` — formal URD analysis

## Estimated Implementation Effort

| Phase | Scope | Effort |
|-------|-------|--------|
| Phase 1 | base-cache-starter upgrade (encryption + serialization) | 3-4 days |
| Phase 2 | auth-service migration | 2-3 days |
| Phase 3 | Additional cache candidates (PasswordPolicy, i18n, CipherKey) | 1-2 days |
| **Total** | | **6-9 days** |

## Research Artifacts
| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| [opensource_findings.md](./opensource_findings.md) | Open source evaluation (JetCache, J2Cache, base-cache-starter) |
| [web_research.md](./web_research.md) | Internet research (encryption, serialization, data types) |
| [comparison_analysis.md](./comparison_analysis.md) | Comparison matrix + build vs buy decision |
| [business_analysis.md](./business_analysis.md) | Business analysis (5 Use Cases) |
| [technical_spec.md](./technical_spec.md) | Technical specification (architecture, data flow, API, testing) |
| [validation_report.md](./validation_report.md) | Quality review results (ALL PASS) |
