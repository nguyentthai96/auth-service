# Proposal: Performance Benchmark Testing — Full TPS Evaluation

## 1. Summary

Mở rộng framework kiểm thử hiệu năng auth-service từ nền tảng archive (10 FRs: datasource-proxy, WireMock, K6 basic, JMH basic) sang hệ thống benchmark TPS toàn diện với observability stack, infrastructure tuning, và comprehensive test coverage.

**Classification:** EXTEND | Command | Testing Infrastructure
**Archive:** `2026-08-24-tps-performance-testing` (10 FRs implemented)
**New Scope:** 21 FRs (16 URD + 5 Enriched)

## 2. Problem Statement

Auth-service hiện có framework testing cơ bản (4 K6 scripts, 2 JMH benchmarks) nhưng thiếu:
1. **Observability** — Không có Prometheus/Grafana integration cho real-time monitoring
2. **Coverage** — 4 K6 scripts chỉ bao phủ ~4/23 endpoints
3. **User Journey** — Chưa có chain tests mô phỏng flows thực tế (login→profile→refresh)
4. **Stress/Soak** — Chưa có tests tìm breaking point hoặc memory leak
5. **Infrastructure baseline** — HikariCP, JVM GC, Redis serializer chưa được tune/benchmark
6. **Reporting** — Không có structured report template cho bottleneck analysis

## 3. Proposed Solution

### 3.1 Approach: Bottom-Up Layered with Observability-First

```
Layer 1: Observability Stack       → Prometheus + Grafana + Actuator config
Layer 2: Infrastructure Tuning     → HikariCP + JVM ZGC + Virtual Threads
Layer 3: K6 Test Expansion         → 15+ scripts covering 23 endpoints + 8 chains
Layer 4: JMH Expansion             → BCrypt, JWT, Redis serializer benchmarks
Layer 5: Documentation             → Report template + Bottleneck checklist
```

### 3.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Observability TRƯỚC tests | Cần monitoring data ngay từ lần test đầu tiên |
| `base-observability-starter` reuse | Đã có `micrometer-registry-prometheus` — chỉ config |
| Env-var driven HikariCP config | Zero behavioral change ở defaults, tunable per environment |
| K6 scripts grouped by priority/domain | Giảm file count (15 vs 23), vẫn isolate per-endpoint metrics via tags |
| Configurable traffic mix ratios | Tạm dùng 60/20/10/5/5, adjust khi có access log data thực |
| JMH benchmarks extend existing structure | Follow `EncryptionBenchmark.kt` pattern |

## 4. Scope

### In Scope
- Prometheus + Grafana Docker Compose extension (`compose-perf.yaml`)
- Spring Boot Actuator prometheus endpoint + histogram config
- HikariCP explicit pool config (2 profiles: 4-core/8GB, 8-core/32GB)
- JVM ZGC flags + virtual thread pinning detection
- K6: 4 single API script groups, 8 chain scripts, 3 system tests (full/stress/soak)
- K6 → Prometheus remote write integration
- Centralized SLA thresholds + environment config
- JMH: BCrypt, JWT RS256, Redis serializer comparison benchmarks
- Grafana dashboard provisioning (JSON auto-provision)
- Tomcat access log config for traffic analysis
- Performance report template + bottleneck analysis checklist (USE Method)

### Out of Scope
- Production business logic changes
- Database schema changes
- API contract changes
- CI/CD pipeline modifications (FR-016 prepares thresholds, CI integration is separate)
- External service (SSO, Captcha) real integration — use WireMock mocks

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Prometheus endpoint exposed in all environments | Medium | Low | Read-only endpoint, no side effects |
| HikariCP config breaks existing behavior | Low | Medium | Env var defaults match current behavior |
| K6 Prometheus remote write stability | Low | Low | Feature moved to stable in K6 v0.49+ |
| Soak test Docker container restart | Medium | Low | `restart: unless-stopped` + health checks |

## 6. Implementation Phases

| Phase | FRs | Duration | Dependencies |
|-------|-----|----------|-------------|
| 1. Observability Stack | FR-001, FR-002, FR-014, FR-018, FR-021 | 2 days | None |
| 2. Infrastructure Tuning | FR-003, FR-004, FR-020 | 1 day | Phase 1 |
| 3. K6 Test Expansion | FR-005→009, FR-013, FR-016, FR-019 | 5 days | Phase 1, 2 |
| 4. JMH Expansion | FR-010, FR-011, FR-012 | 2 days | Phase 2 |
| 5. Documentation | FR-015, FR-017 | 1 day | Phase 3, 4 |

**Total estimated: ~11 days**

## 7. Success Criteria

- [ ] Prometheus scrapes auth-service metrics every 15s
- [ ] Grafana dashboard auto-provisions with 4 rows (Performance, Utilization, Infrastructure, Per-Endpoint)
- [ ] K6 tests cover 23/23 API endpoints
- [ ] K6 chain tests pass with >99% success rate
- [ ] Full load test reaches ≥500 TPS sustained with P99 <1000ms
- [ ] Stress test identifies breaking point with recovery verification
- [ ] Soak test (1h) shows no memory leak
- [ ] JMH benchmarks produce JSON results for BCrypt, JWT, Redis serializer
- [ ] Performance report template used for at least 1 bottleneck analysis cycle
