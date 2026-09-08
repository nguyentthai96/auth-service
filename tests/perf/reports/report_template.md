# Performance Report — auth-service

> Date: `YYYY-MM-DD` | Environment: `<env>` | Tester: `<name>`

---

## 1. Executive Summary

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Peak TPS | ≥500 | `_____` | ⬜ |
| P95 Latency (Login) | <200ms | `_____` ms | ⬜ |
| P99 Latency (System) | <1000ms | `_____` ms | ⬜ |
| Error Rate | <0.1% | `_____`% | ⬜ |
| Breaking Point TPS | N/A | `_____` | ℹ️ |
| Soak Test Duration | `_____`h | Passed/Failed | ⬜ |

**Overall Assessment**: `PASS / FAIL / CONDITIONAL PASS`

---

## 2. Test Configuration

### Environment
| Component | Spec |
|-----------|------|
| Application Server | CPU: `_____` cores, RAM: `_____` GB |
| Database Server | CPU: `_____` cores, RAM: `_____` GB |
| Redis | Version: `_____`, Memory: `_____` MB |
| JVM | Version: `_____`, Flags: `_____` |
| HikariCP Pool | Max: `_____`, Min-Idle: `_____` |
| K6 Runner | Host: `_____`, VUs: `_____` |

### Test Parameters
| Parameter | Value |
|-----------|-------|
| Traffic Mix | Auth: `__`% / Profile: `__`% / Register: `__`% / Admin: `__`% / MFA+SSO: `__`% |
| Ramp Profile | `_____` |
| Duration | `_____` |
| JVM Flags | `_____` |

---

## 3. Baseline Metrics (Per-Endpoint)

### Infrastructure Baseline
| Component | Metric | Result |
|-----------|--------|--------|
| Health Endpoint | Max TPS | `_____` req/s |
| DB Pool (size=`__`) | Max concurrent queries before wait | `_____` |
| Redis | GET/SET ops/s | `_____` |

### Single API Endpoints
| Endpoint | TPS | P50 (ms) | P95 (ms) | P99 (ms) | Error % |
|----------|-----|----------|----------|----------|---------|
| `POST /auth/login` | | | | | |
| `POST /auth/refresh` | | | | | |
| `POST /auth/validate` | | | | | |
| `POST /auth/register` | | | | | |
| `GET /auth/sessions` | | | | | |
| `POST /auth/mfa/verify` | | | | | |
| `POST /internal/validate` | | | | | |
| `GET /roles` | | | | | |
| `GET /users` | | | | | |

### Chain Tests
| Chain | Steps | P50 (ms) | P95 (ms) | Success Rate |
|-------|-------|----------|----------|--------------|
| Auth Basic | Login→Profile→Refresh→Logout | | | |
| Auth MFA | Login→MFA→Verify→Profile | | | |
| Registration | Register→Login→Profile | | | |
| SSO | Init→Callback→Profile | | | |
| Session Mgmt | Login→Sessions→Revoke | | | |
| Admin | Login→Users→AssignRole→Verify | | | |

---

## 4. Bottleneck Analysis (USE Method)

### Resource Analysis
| Resource | Utilization | Saturation | Errors | Bottleneck? |
|----------|-------------|------------|--------|-------------|
| CPU | `__`% avg | `__`% peak | | ⬜ |
| JVM Heap | `__` MB used / `__` MB max | GC pause: `__` ms | OOM? | ⬜ |
| DB Pool | `__` active / `__` total | `__` pending | Timeouts? | ⬜ |
| Redis | `__` ops/s | `__` ms latency | Connection errors? | ⬜ |
| HTTP Threads | `__` active | Queued: `__` | Rejections? | ⬜ |

### Identified Bottlenecks
1. **`<Resource>`**: Description of bottleneck, evidence from metrics
2. **`<Resource>`**: Description of bottleneck, evidence from metrics

---

## 5. Optimization Results (Before/After)

### Optimization 1: `<Title>`
| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| P95 Latency | `__` ms | `__` ms | `__`% |
| TPS | `__` | `__` | `__`% |

**Change**: `<brief description of what was changed>`

### Optimization 2: `<Title>`
| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| | | | |

---

## 6. JMH Microbenchmark Results

| Benchmark | ops/sec | Notes |
|-----------|---------|-------|
| BCrypt hashPassword (strength=12) | `_____` | Login bottleneck ceiling |
| BCrypt verifyPassword | `_____` | |
| JWT signRS256 | `_____` | Token creation throughput |
| JWT verifyRS256 | `_____` | Per-request validation |
| AES-GCM encrypt (small) | `_____` | E2EE overhead |
| Jackson serialize (UserSession) | `_____` | Cache write cost |
| Jackson deserialize (UserSession) | `_____` | Cache read cost |
| JDK serialize (UserSession) | `_____` | Comparison baseline |

---

## 7. Recommendations (Prioritized by Impact)

| Priority | Recommendation | Expected Impact | Effort |
|----------|---------------|-----------------|--------|
| P0 | | | |
| P1 | | | |
| P2 | | | |

---

## 8. Appendix

### Grafana Dashboard Screenshots
> Embed or link Grafana dashboard screenshots here

### Test Execution Logs
> K6 summary output, JMH JSON output paths

### Prometheus Queries Used
```promql
# TPS
sum(rate(http_server_requests_seconds_count{application="auth-service"}[1m]))

# P95 Latency
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="auth-service"}[1m])) by (le))

# Error Rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count[1m])) * 100

# HikariCP Saturation
hikaricp_connections_pending{application="auth-service"} > 0
```
