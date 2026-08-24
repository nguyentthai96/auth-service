# Phân tích so sánh: TPS Performance Testing

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | TPS Performance Testing |
| **Ngày phân tích** | 2026-08-21 |
| **Recommendation** | **Hybrid approach — Build custom framework using K6 + datasource-proxy + JMH + WireMock** |
| **Rationale** | Không có single tool giải quyết tất cả requirements. Mỗi layer testing (E2E / Integration / Micro) cần tool chuyên biệt. Project đã invest K6, chỉ cần extend. |
| **Confidence** | **HIGH** — tất cả tools đều proven, đã có precedent trong project |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | K6 + datasource-proxy + JMH + WireMock (Hybrid) | Custom Build (compose) | Mỗi tool cho 1 layer: K6 (E2E TPS), datasource-proxy (SQL), JMH (micro), WireMock (HTTP) | Full coverage, tool-per-layer, proven each | Multi-tool learning curve | ✅ | 9.2 |
| 2 | Gatling all-in-one | Open Source | Gatling for E2E + Gatling Feeders for data + JVM-based | Single tool, JVM native, rich reports | No SQL counting, no microbenchmark, switching cost from K6 | ⚠️ | 6.5 |
| 3 | QuickPerf + K6 (minimal) | Open Source | QuickPerf for SQL assertions, K6 for E2E | Annotation-based SQL, less code | Less flexible, less maintained, no WireMock/JMH | ⚠️ | 6.0 |
| 4 | Commercial (K6 Cloud + Gatling Enterprise) | Commercial | SaaS platforms for load testing | Zero infra, collaboration, trends | Cost, data leaves org, vendor lock-in | ❌ | 4.0 |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Hybrid (K6+dp+JMH+WM) | Gatling All-in-one | QuickPerf + K6 | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| E2E HTTP Load Testing | ✅ (K6) | ✅ | ✅ (K6) | ⭐ Must |
| TPS Measurement | ✅ (K6 `http_reqs` metric) | ✅ | ✅ (K6) | ⭐ Must |
| SQL Query Count Assertion | ✅ (datasource-proxy) | ❌ | ✅ (QuickPerf) | ⭐ Must |
| HTTP Client Latency Simulation | ✅ (WireMock) | ❌ | ❌ | ⭐ Must |
| JVM Microbenchmark | ✅ (JMH) | ❌ | ❌ | Nice to have |
| CI/CD Threshold Gate | ✅ (K6 exit code 99) | ⚠️ (need config) | ✅ (K6) | ⭐ Must |
| Cache Encryption Correctness Test | ✅ (Testcontainers + Spring) | ❌ | ❌ | ⭐ Must |
| Docker-native Execution | ✅ (K6 Docker) | ⚠️ (JVM startup) | ✅ (K6 Docker) | ⭐ Must |
| Existing Integration | ✅ (K6 already in project) | ❌ (new tool) | ⚠️ (K6 yes, QP new) | ⭐ Must |
| Kotlin DSL Support | ✅ (custom DSL) | ⚠️ (Scala DSL primary) | ❌ | Nice to have |
| **Coverage** | **10/10** | **3/10** | **5/10** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 8 | 38% - 100% |
| Nice to have | 2 | 0% - 100% |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Hybrid (K6+dp+JMH+WM) | Gatling | QuickPerf+K6 | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| FR-001: assertQueryCount | UC-001 | ✅ | ❌ | ✅ | Gatling — no SQL counting |
| FR-002: WireMock HTTP simulation | UC-002 | ✅ | ❌ | ❌ | Gatling, QuickPerf — no mock |
| FR-003: K6 E2E 500 VUs | UC-003 | ✅ | ✅ | ✅ | None |
| FR-004: K6 Gradle task | UC-003 | ✅ | ⚠️ | ✅ | Gatling — different plugin |
| FR-005: JMH microbenchmark | UC-004 | ✅ | ❌ | ❌ | Gatling, QP — no JMH |
| FR-006: CI/CD fail fast | UC-003 | ✅ | ⚠️ | ✅ | Gatling — config needed |
| FR-007: Cache encryption test | UC-005 | ✅ | ❌ | ❌ | Gatling, QP — not applicable |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| K6 Scripts | 2 basic scripts (50 VUs, 10s) | Multiple scenarios (500 VUs, constant-arrival-rate, cache modes) | Major expansion needed | HIGH |
| SQL Query Assertion | None — manual log reading | `assertQueryCount {}` DSL + datasource-proxy | New capability | HIGH |
| HTTP Mock | None | WireMock latency/fault simulation | New capability | MEDIUM |
| JMH Benchmark | None | `src/jmh/kotlin/` benchmarks for encryption | New capability | LOW |
| Cache Encryption Test | Basic `CacheEncryptionIntegrationTest` | Full correctness test (NONE/FULL/PARTIAL raw data) | Extend existing | MEDIUM |
| CI/CD Performance Gate | No threshold enforcement | K6 thresholds + exit code 99 fail | New capability | HIGH |
| Gradle Tasks | 2 tasks (k6Run, k6ProfileRun) | Extended tasks + JMH task | Extend existing | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build (Hybrid) | Reuse Gatling | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 5-8 dev-days | 8-12 dev-days (migration) | Custom Build |
| Maintenance burden | Medium (4 tools) | Low (1 tool) | Gatling |
| Feature coverage | 100% (10/10) | 30% (3/10) | Custom Build |
| Integration effort | Low (K6 already integrated) | High (new tool) | Custom Build |
| Long-term flexibility | High (swap any tool) | Medium (locked to Gatling) | Custom Build |
| Risk | Low (proven tools) | Medium (migration risk) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Hybrid (K6+dp+JMH+WM) | Gatling All-in-one | QuickPerf + K6 |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 10 | 3 | 5 |
| Integration ease | 25% | 9 | 4 | 7 |
| Maintenance | 20% | 7 | 8 | 6 |
| Community/Support | 15% | 9 | 8 | 5 |
| Learning curve | 10% | 6 | 7 | 7 |
| **Tổng điểm (weighted)** | | **8.65** | **5.15** | **5.95** |

### Reasoning

**Recommended approach**: Build custom framework composing K6 + datasource-proxy + JMH + WireMock

**Lý do**:
1. **Full requirement coverage** — only Hybrid approach covers all 7 FRs (100% coverage vs 30-50% for alternatives). Each tool is best-in-class for its layer.
2. **Minimal migration cost** — K6 đã integrated (2 scripts, 2 Gradle tasks). Chỉ cần extend, không rebuild. datasource-proxy + JMH + WireMock là additions, không replacements.
3. **CI/CD native** — K6 exit code 99 mechanism is purpose-built for CI/CD gating. Không cần custom exit code parsing.

**Trade-offs chấp nhận**:
- Multi-tool learning curve — chấp nhận vì mỗi tool có scope rõ ràng, không overlap. Developer chỉ cần học tool cho layer mình test.
- Maintenance của 4 tool dependencies — chấp nhận vì tất cả tools đều actively maintained (K6 by Grafana, WireMock by WireMock Inc, JMH by OpenJDK, datasource-proxy stable/mature).

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| JMH plugin incompatible với JVM 25 | LOW | LOW | Fallback: use JMH jar directly, pin compatible version |
| datasource-proxy conflicts with HikariCP in test context | LOW | MEDIUM | Use `DataSourceProxyBeanPostProcessor` pattern, wrap after HikariCP init |
| K6 Docker container network issues in CI | LOW | HIGH | Use `--network host` (already in place), document CI Docker setup |
| WireMock port conflicts in parallel test execution | MEDIUM | LOW | Use `dynamicPort()` in WireMock JUnit extension |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Hybrid (K6+dp+JMH+WM) | 5-8 days | MEDIUM | LOW (tools are stable) |
| Gatling Migration | 8-12 days | HIGH (migration + learning) | LOW |
| QuickPerf + K6 | 4-6 days | LOW | MEDIUM (QuickPerf less maintained) |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
