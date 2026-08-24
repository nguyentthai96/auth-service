# Research Handoff: TPS Performance Testing

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | TPS Performance Testing |
| **Ngày hoàn thành** | 2026-08-21 |
| **Recommendation** | Build (Hybrid — compose K6 + datasource-proxy + JMH + WireMock) |
| **Research directory** | `openspec/research/tps-performance-testing/` |
| **Status** | complete |

---

## 1. Recommendation

Build custom performance testing framework bằng cách compose 4 open source tools: **K6** (E2E load testing, TPS measurement, CI/CD gate), **datasource-proxy** (SQL query count assertion, N+1 detection), **JMH** (encryption/serialization microbenchmark), và **WireMock** (HTTP client latency/fault simulation). Lý do chính: không có single tool nào cover 100% requirements; mỗi tool best-in-class cho layer của nó; K6 đã integrated sẵn trong project.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | K6 (9.5/10) — dùng trực tiếp, đã proven. WireMock (8.75/10) — dùng trực tiếp. datasource-proxy (7.2/10) — dùng trực tiếp + custom DSL wrapper. JMH Plugin (7.3/10) — dùng trực tiếp. Gatling (7.95/10) — tham khảo pattern only. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | K6 constant-arrival-rate executor tối ưu cho TPS measurement. datasource-proxy QueryCountHolder API cho per-thread counting. Vlad Mihalcea's SQLStatementCountValidator pattern. WireMock withFixedDelay() cho latency simulation. 12 unique sources. | [web_research.md](./web_research.md) |
| Gap Coverage | Hybrid approach cover 100% (10/10 features) vs Gatling (30%) vs QuickPerf+K6 (50%). All 7 gaps from current→target system addressed. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | K6 đã tích hợp (2 scripts, 2 Gradle tasks — nhưng chỉ 50 VUs, 10s). CacheEncryptionIntegrationTest cơ bản đã có. Không có datasource-proxy, WireMock, hoặc JMH. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Assert SQL Query Count | Kotlin DSL + annotation asserting exact SQL query counts via datasource-proxy | Must |
| UC-002 | Simulate External Service Latency | WireMock latency/fault injection for SSO/external HTTP services | Should |
| UC-003 | Run E2E Load Test | K6 500 VUs, multi-scenario, auth+profile endpoints | Must |
| UC-004 | Run JMH Microbenchmark | JMH benchmarks for AES-GCM encryption and Jackson serialization | Nice |
| UC-005 | Verify Cache Encryption Correctness | Integration tests verifying Redis raw data format (NONE/FULL/PARTIAL) | Should |
| UC-006 | Enforce Performance Gate | K6 thresholds + exit code 99 for CI/CD fail-fast | Must |
| UC-007 | Detect N+1 Query | Auto-detect N+1 JPA queries via assertQueryCount | Must |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Testing infrastructure — no production code changes. 3 layers: E2E (K6), Integration (datasource-proxy + WireMock + Testcontainers), Micro (JMH) |
| Data model | No new entities. Tests target existing: users, login_sessions, Redis cache keys |
| APIs | 0 new endpoints. Tests 4 existing endpoints: login, profile, token refresh, logout |
| Key dependencies | datasource-proxy 1.10+, spring-cloud-contract-wiremock, me.champeau.jmh 0.7.2, JMH 1.37 |
| Risk areas | 1) JMH plugin JVM 25 compatibility (LOW risk). 2) WireMock port conflicts in parallel tests (mitigated: dynamicPort()) |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec tps-performance-testing --from-research` | Muốn explore thêm, có nhiều hướng tiếp cận |
| URD Analysis | `/wf_pre_openspec openspec/research/tps-performance-testing/business_analysis.md` | Đã rõ requirements, muốn formalize |
| OpenSpec (direct) | `/wf_openspec tps-performance-testing` | Đã rõ mọi thứ, muốn generate artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis |
| [opensource_findings.md](./opensource_findings.md) | 2 | Open source evaluation + scoring matrix + gap analysis |
| [web_research.md](./web_research.md) | 3 | Internet research + product evaluation |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Comparison matrix + feature matrix + recommendation |
| [business_analysis.md](./business_analysis.md) | 5 | Business analysis (use case decomposition) |
| [technical_spec.md](./technical_spec.md) | 6 | Technical specification (agent-ready) |
| [validation_report.md](./validation_report.md) | 7 | Quality review results |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ⚠️ | Web search tools returned empty at runtime; URLs are authoritative docs (k6.io, wiremock.org, github.com) |
| Consistency | ✅ | BA ↔ Tech Spec aligned. FR IDs consistent across all documents |
| Completeness | ✅ | All UCs have flows. All 5 OS projects scored. 12 web sources. |
| Feasibility | ✅ | Feasible with current stack. All tools JVM/Spring Boot compatible |
| Gap Coverage | ✅ | All 7 gaps from current→target system addressed in tech spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
