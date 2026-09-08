# Handoff Summary — Performance Benchmark & TPS Testing

## 1. Executive Summary

Nghiên cứu này cung cấp **chiến lược performance testing toàn diện** cho auth-service, từ infrastructure baseline đến full-system load testing. Hệ thống hiện tại đã có nền tảng tốt (K6 + JMH) nhưng cần mở rộng đáng kể để đạt được mục tiêu benchmark TPS toàn diện.

### Key Decision: **Extend & Integrate**
- **Extend** K6 (3 scripts → 15+ scenarios) và JMH (2 → 7+ benchmarks)
- **Integrate** Prometheus + Grafana cho real-time observability
- **Tune** HikariCP, JVM (ZGC), Actuator cho production readiness
- **Không** thêm tool mới (Gatling/JMeter) — K6 đáp ứng đủ

---

## 2. Current State Assessment

| Area | Current | Coverage |
|------|---------|----------|
| K6 Load Tests | 3 scripts (login, profile, cache) | 30% |
| JMH Benchmarks | 2 benchmarks (AES, JSON) | 30% |
| Observability | Actuator basic (health, info, metrics) | 15% |
| Infrastructure Config | Defaults (HikariCP, JVM, Lettuce) | 5% |
| Report Structure | K6 console output only | 15% |

---

## 3. Proposed Architecture

### 5-Layer Testing Pyramid

```
         ┌─────────────────────┐
         │ Full System Load    │ ← K6 mixed workload (500+ VUs)
         ├─────────────────────┤
         │ API Chain Tests     │ ← K6 sequential flows (8 chains)
         ├─────────────────────┤
         │ Single API Tests    │ ← K6 per-endpoint (23 endpoints)
         ├─────────────────────┤
         │ Component Benchmark │ ← JMH microbenchmarks (7 suites)
         ├─────────────────────┤
         │ Infrastructure Base │ ← HikariCP + Redis + JVM + Crypto
         └─────────────────────┘
```

### Observability Stack

```
K6 ──→ Prometheus ──→ Grafana
              ↑
Auth Service (Actuator/Micrometer)
```

---

## 4. Key Deliverables

### 4.1 Infrastructure Changes (Sprint 1)

| Change | File | Impact |
|--------|------|--------|
| HikariCP explicit config | `application-db.yml` | DB pool optimization |
| JVM flags (ZGC) | `gradle.properties` / Docker | GC latency reduction |
| Actuator prometheus endpoint | `application-core-observability.yml` | Enable metrics scraping |
| Docker compose extension | `compose-perf.yaml` | Prometheus + Grafana |

### 4.2 Test Artifacts (Sprint 2-3)

| Artifact | Count | Priority |
|----------|-------|----------|
| K6 single API scenarios | 15+ | P0 |
| K6 chain scenarios | 8 | P0 |
| K6 system tests (load/stress/soak) | 3 | P0/P1 |
| JMH benchmarks (BCrypt, JWT, Redis) | 5 new | P0 |
| Grafana dashboard configs | 2 | P0 |
| Prometheus config | 1 | P0 |
| Performance report template | 1 | P0 |

### 4.3 SLA Targets

| Endpoint Category | P95 Latency | P99 Latency | Error Rate |
|-------------------|-------------|-------------|------------|
| Auth (login/refresh) | < 200ms | < 500ms | < 1% |
| Profile/Cache | < 100ms | < 200ms | < 1% |
| CRUD Operations | < 500ms | < 1000ms | < 1% |
| System Overall | < 500ms | < 1000ms | < 0.1% |
| Target TPS | ≥ 500 req/s sustained | — | — |

---

## 5. Optimization Targets Identified

| # | Target | Expected Impact | Effort |
|---|--------|----------------|--------|
| 1 | HikariCP tuning (pool-size=10) | Prevent DB pool exhaustion | Low |
| 2 | ZGC garbage collector | Reduce p99 latency spikes | Low |
| 3 | Prometheus metrics endpoint | Enable real-time monitoring | Low |
| 4 | Redis serialization review | Potential cache speedup | Medium |
| 5 | Virtual thread pinning detection | Prevent hidden bottlenecks | Low |
| 6 | BCrypt strength analysis | Balance security vs speed | Medium |
| 7 | Query optimization (datasource-proxy) | Eliminate N+1 if exists | Medium |

---

## 6. Risk Assessment

| Risk | Level | Mitigation |
|------|-------|-----------|
| Test environment ≠ Production | MEDIUM | Document all differences, scale results |
| Load generator as bottleneck | LOW | K6 Go runtime efficient, Docker --network host |
| External deps during load test | MEDIUM | Mock SSO/Captcha with WireMock |
| Data exhaustion | LOW | SharedArray with generated test data |
| Soak test stability | MEDIUM | Start with 1h, extend gradually |

---

## 7. Next Steps — Pipeline Integration

Tài liệu này có thể được sử dụng làm input cho:

| Workflow | Purpose | How to use |
|----------|---------|-----------|
| `/wf_pre_openspec` | Generate URD từ business analysis | Feed `business_analysis.md` |
| `/wf_brainstorm_openspec` | Deep thinking with research context | Feed toàn bộ research folder |
| `/wf_openspec` | Generate implementation artifacts | Feed `technical_spec.md` |
| Direct Implementation | Code trực tiếp theo technical spec | Follow Sprint plan |

### Recommended Path: **Direct Implementation**

Vì feature này chủ yếu là tạo test scripts + config changes (không phải business logic mới), recommend đi thẳng vào implementation theo Sprint plan trong `technical_spec.md`.

---

## 8. Generated Files

```
openspec/research/performance-benchmark-testing/
├── ✅ research_brief.md          — Phase 1: Scope & keywords
├── ✅ opensource_findings.md      — Phase 2: Tool evaluation
├── ✅ web_research.md             — Phase 3: Internet research
├── ✅ comparison_analysis.md      — Phase 4: Gap analysis
├── ✅ business_analysis.md        — Phase 5: Use cases & rules
├── ✅ technical_spec.md           — Phase 6: Architecture & implementation
├── ✅ validation_report.md        — Phase 7: Quality checks
└── ✅ handoff_summary.md          — This file
```

---

## Review Status

| Check | Result |
|-------|--------|
| Source Verification | ✅ PASS |
| Consistency | ✅ PASS |
| Completeness | ✅ PASS |
| Feasibility | ✅ PASS |
| Gap Coverage | ✅ PASS |
