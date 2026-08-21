# Research Handoff: Anonymous Login Optimization

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | Anonymous Login Optimization |
| **Ngày hoàn thành** | 2025-07-15 |
| **Recommendation** | Build in-place optimizations (pipeline + Lua scripts) |
| **Research directory** | `openspec/research/anonymous-login-optimization/` |
| **Status** | complete |

---

## 1. Recommendation

**Optimize in-place** using Spring Data Redis pipeline (`executePipelined`) and Lua scripts (`execute(RedisScript)`) with zero new external dependencies. The existing anonymous login system has 6 optimization areas: Redis round-trip reduction via pipelining, sliding window rate limiting via Lua script, batch data transfer during promotion, safe lock release with UUID ownership, running data size counter, and observability spans. All optimizations use existing Spring Data Redis APIs already available in the project.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | 4 solutions evaluated (Redisson 8.95, Spring Data Redis Pipeline 8.45, Bucket4j 7.85, Resilience4j 7.65) — Spring Data Redis Pipeline selected as primary approach (zero new deps); Redisson/Bucket4j referenced for algorithms only | [opensource_findings.md](./opensource_findings.md) |
| Web Research | 8 unique sources across 3 iterations; Redis Labs sliding window counter algorithm, Kleppmann's safe lock release pattern, Spring Data executePipelined API — all well-documented, battle-tested | [web_research.md](./web_research.md) |
| Gap Coverage | 100% gap coverage — all 6 optimization areas addressed in tech spec with concrete code examples and Lua script implementations | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | Anonymous login fully implemented: AnonymousSessionHandler (3-4 RTT → target 2), AnonymousRateLimitService (fixed window → sliding), AnonymousSessionDataService (per-key → batch), SessionPromotionService (unsafe DEL → Lua safe release) | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-OPT-001 | Pipeline Session Creation | Batch HSET+EXPIRE into 1 pipeline RTT (from 3-4 RTTs) | Must |
| UC-OPT-002 | Sliding Window Rate Limiting | Replace fixed-window INCR+EXPIRE with Lua sliding window counter | Must |
| UC-OPT-003 | Batch Data Transfer | Replace per-key GET+SET loop with pipeline MGET+MSET (from 1+2N RTT to 3 RTT) | Must |
| UC-OPT-004 | Safe Lock Release | Replace simple DEL with Lua conditional DEL using UUID ownership verification | Should |
| UC-OPT-005 | Running Size Counter | Replace SCAN+STRLEN (O(N)) with HINCRBY running counter (O(1)) | Should |
| UC-OPT-006 | Observability Spans | Add Micrometer Observation spans to critical anonymous session paths | Nice |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | No architectural changes — internal optimization of existing services using Spring Data Redis pipeline and Lua scripting |
| Data model | 1 change: add `dataSize` field to `anon:session:{id}` Redis hash. Rate limit keys change from `anon:rate:{ip}` to `anon:rate:{ip}:{windowId}` (sliding window) |
| APIs | 0 API changes — all optimizations are internal, same request/response contracts |
| Key dependencies | No new dependencies — uses existing StringRedisTemplate, executePipelined(), execute(RedisScript) |
| Risk areas | (1) Lua script errors in production — mitigated by comprehensive tests + SHA1 caching; (2) Running size counter drift — mitigated by periodic SCAN reconciliation |
| New artifacts | 2 Lua scripts: `sliding_window_rate_limit.lua` (27 lines), `safe_lock_release.lua` (8 lines). 1 config class: `RedisLuaScriptConfig`. |
| Effort estimate | 3-5 developer-days |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec anonymous-login-optimization --from-research` | Muốn explore thêm — e.g., full Redisson migration, Redis Cluster pipelining |
| URD Analysis | `/wf_pre_openspec openspec/research/anonymous-login-optimization/business_analysis.md` | Đã rõ requirements, muốn formalize into URD |
| OpenSpec (direct) | `/wf_openspec anonymous-login-optimization` | Đã rõ mọi thứ, muốn generate implementation artifacts (tasks, design, migration) |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, 5 primary + 8 secondary keywords, 8 search queries, current system analysis (6 optimization areas, 8 code patterns documented) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 4 projects evaluated with scoring matrix (Redisson, Spring Data Redis Pipeline, Bucket4j, Resilience4j), gap analysis per project |
| [web_research.md](./web_research.md) | 3 | 3 search iterations, 8 unique sources, 6 optimization patterns documented with trade-offs |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Feature matrix (9 features × 4 solutions + current), gap analysis (6 requirements, 7 system aspects), decision matrix (5 criteria weighted), cost estimate |
| [business_analysis.md](./business_analysis.md) | 5 | 6 optimization use cases, 6 FRs, 5 NFRs, 12 business rules, traceability matrix, glossary |
| [technical_spec.md](./technical_spec.md) | 6 | Architecture diagrams, Redis key changes, 2 sequence diagrams, before/after performance tables, 2 Lua scripts, 3 code change examples, 5 classes to modify, 15 test cases |
| [validation_report.md](./validation_report.md) | 7 | All 5 checks PASS on first iteration |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 8 sources have valid URLs (Redis docs, Spring Data docs, Kleppmann) |
| Consistency | ✅ | BA ↔ Tech Spec aligned (UCs → class changes, FRs → code examples, BRs consistent) |
| Completeness | ✅ | All UCs have flows; all Lua scripts provided; all code change examples complete |
| Feasibility | ✅ | Feasible with current stack — no new dependencies; all APIs verified (executePipelined, execute(RedisScript)) |
| Gap Coverage | ✅ | All 6 optimization gaps addressed with concrete implementations |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
