# Business Analysis — Performance Benchmark & TPS Testing

## 1. Feature Context

### 1.1 Tại sao cần Performance Testing?

Hệ thống auth-service là **single point of failure** cho toàn bộ platform — mọi request từ mobile/web đều phải qua authentication & authorization. Nếu auth-service chậm hoặc sập dưới tải:
- **Toàn bộ user** không thể login, access tính năng
- **Cascade failure** sang các service khác (timeout → retry storm → resource exhaustion)
- **Revenue impact** trực tiếp nếu là production system

### 1.2 Business Objectives

| Objective | Measurable Target |
|-----------|------------------|
| Xác định TPS tối đa | Biết chính xác số request/second hệ thống chịu được |
| Xác định breaking point | Biết tại mức tải nào hệ thống bắt đầu degradation |
| Baseline per-API | Mỗi endpoint có p95 latency benchmark riêng |
| Bottleneck map | Biết chính xác layer nào gây nghẽn (DB/Redis/Crypto/HTTP) |
| Optimization roadmap | Plan chi tiết cải thiện TPS theo từng iteration |
| Regression prevention | CI/CD gates ngăn performance regression |

---

## 2. Use Cases

### UC-001: Infrastructure Baseline Measurement

**Mô tả semantic:** Đo lường capacity cơ bản của từng infrastructure component (database pool, Redis, HTTP server, crypto engine) TRƯỚC KHI test application logic. Điều này giúp xác định upper bound — hệ thống không thể nhanh hơn component chậm nhất.

**Actor:** DevOps/Performance Engineer

**Preconditions:**
- Docker compose running (PostgreSQL + Redis)
- Auth-service đang chạy
- JMH + K6 configured

**Basic Flow:**
1. Run JMH benchmark suite → Đo crypto/serialization throughput
2. Run DB connection pool stress → Đo HikariCP capacity
3. Run Redis connection stress → Đo Lettuce throughput
4. Run HTTP server bare endpoint → Đo Tomcat capacity
5. Collect & record baseline numbers

**Exception Flows:**
- E1: DB connection refused → Check Docker compose health
- E2: JMH results inconsistent → Increase warmup iterations

**Expected Output:**
```
Infrastructure Baseline Report:
├── AES-GCM encrypt: X ops/sec
├── BCrypt hash: X ops/sec
├── JWT sign: X ops/sec
├── Jackson serialize: X ops/sec
├── HikariCP max active: X connections
├── Redis commands/sec: X
├── HTTP max concurrent: X connections
└── Bottleneck: [Component with lowest capacity]
```

---

### UC-002: Single API Endpoint Benchmark

**Mô tả semantic:** Test từng API endpoint riêng lẻ, độc lập, để xây dựng "bảng chỉ số" baseline latency. Khi thay đổi code, so sánh lại với baseline để detect regression.

**Actor:** Developer/QA

**Preconditions:**
- Test user account created
- Auth-service running
- Baseline infrastructure metrics known (from UC-001)

**Basic Flow:**
1. Chọn target endpoint (ví dụ: `POST /api/v1/auth/login`)
2. Cấu hình K6 scenario: constant-arrival-rate, 100 req/s, 30s
3. Run K6 script
4. Collect metrics: p50, p95, p99, error rate, TPS
5. Record vào baseline database/report
6. Lặp lại cho endpoint tiếp theo

**Exception Flows:**
- E1: Endpoint cần auth token → Setup function lấy token trước
- E2: Endpoint cần data seed → Seed data trong setup phase
- E3: Rate limiter block test → Disable rate limit cho test environment

**Business Rules:**
- BR1: P95 latency < 200ms cho critical endpoints (login, token refresh)
- BR2: P95 latency < 500ms cho CRUD endpoints
- BR3: Error rate < 1% cho bất kỳ endpoint nào
- BR4: Test phải chạy đủ 30s để đảm bảo JIT warmup

---

### UC-003: API Chain (Sequential Flow) Testing

**Mô tả semantic:** Test chuỗi API phụ thuộc nhau — mô phỏng user journey thực tế. Một số API chỉ có thể gọi SAU KHI đã gọi API trước đó (ví dụ: phải login trước khi get profile). Chuỗi này phản ánh trải nghiệm thực tế của user.

**Actor:** QA/Performance Engineer

**Preconditions:**
- All individual endpoints tested (UC-002 passed)
- Test data seeded

**Basic Flow:**
1. Define API chain (ví dụ: Login → Get Profile → Update Profile → Logout)
2. Implement K6 script với sequential requests + data correlation
3. Each VU thực hiện toàn bộ chain
4. Measure: end-to-end latency, per-step latency, chain success rate
5. Record results

**Chains cần test:**

| Chain | Steps | Priority |
|-------|-------|----------|
| **Auth Basic** | Login → Profile → Refresh Token → Logout | P0 |
| **Auth + MFA** | Login → MFA Challenge → MFA Verify → Profile | P0 |
| **Registration** | Register → Verify Email → Login → Profile | P0 |
| **SSO** | SSO Init → OAuth2 Callback → Profile | P1 |
| **Session Management** | Login → List Sessions → Revoke Session | P1 |
| **Key Exchange** | Init Key Exchange → Encrypted Request → Response | P1 |
| **Admin** | Login (Admin) → List Users → Assign Role → Verify | P1 |
| **Anonymous → Authenticated** | Anon Token → Browse → Register → Promote Data | P2 |

**Exception Flows:**
- E1: Chain break tại step N → Log which step failed, continue other VUs
- E2: Token expired mid-chain → Implement token refresh logic trong script
- E3: Rate limit hit → Add think time between steps

**Business Rules:**
- BR1: Full chain latency < sum(individual P95) + 20% overhead
- BR2: Chain success rate > 99%
- BR3: Mỗi chain phải test với ≥ 50 concurrent VUs

---

### UC-004: Full System Load Test

**Mô tả semantic:** Mô phỏng production traffic thực tế với mixed workload — nhiều loại user cùng làm nhiều loại action đồng thời. Đây là test gần nhất với real-world performance.

**Actor:** Performance Engineer

**Preconditions:**
- UC-001, UC-002, UC-003 all passed
- Observability stack running (Prometheus + Grafana)

**Basic Flow:**
1. Define traffic mix ratios:
   - 60% Login/Token Refresh (auth-heavy)
   - 20% Profile Access (cache-heavy)
   - 10% Registration/Account management
   - 5% Admin operations
   - 5% MFA/SSO flows
2. Configure K6 multi-scenario test
3. Ramp pattern: 0 → 100 → 500 → 1000 VUs over 10 min
4. Record Grafana metrics during test
5. Analyze: TPS saturation point, resource utilization, error onset

**Exception Flows:**
- E1: System crash under load → Record crash point, reduce VUs, re-test
- E2: External dependency failure → Mock with WireMock
- E3: Data exhaustion → SharedArray with unique users

**Business Rules:**
- BR1: Target TPS: ≥ 500 req/s sustained
- BR2: P99 latency < 1000ms at target TPS
- BR3: Error rate < 0.1% at target TPS
- BR4: No memory leak over 30-minute test

---

### UC-005: Bottleneck Analysis & Optimization Report

**Mô tả semantic:** Sau mỗi lần test, phân tích dữ liệu để xác định bottleneck chính xác, đề xuất optimization, thực hiện optimization, và re-test để verify improvement. Đây là process iterative.

**Actor:** Performance Engineer / Tech Lead

**Preconditions:**
- Load test data collected (UC-004)
- Grafana metrics recorded

**Basic Flow:**
1. Export Grafana metrics → Analysis
2. Apply USE Method per resource:
   - CPU: utilization + context switches
   - Memory: heap usage + GC frequency
   - DB Pool: active connections + pending threads
   - Redis: connected clients + slow log
   - Network: bandwidth + retransmissions
3. Identify top 3 bottlenecks
4. Create optimization plan for #1 bottleneck
5. Implement fix
6. Re-run load test (UC-004)
7. Compare before/after
8. Generate report

**Exception Flows:**
- E1: Multiple bottlenecks tied → Fix easiest/highest impact first
- E2: Bottleneck outside our control (external API) → Document and add circuit breaker
- E3: Fix creates new bottleneck → Document cascade, iterate

**Business Rules:**
- BR1: Mỗi optimization iteration phải có before/after data
- BR2: Improvement < 5% → Consider acceptable, move to next bottleneck
- BR3: Report phải include: metrics, analysis, recommendation, evidence

---

## 3. Traceability Matrix

| Use Case | Business Objective | K6 Script | JMH Benchmark | Grafana Dashboard |
|----------|-------------------|-----------|---------------|-------------------|
| UC-001 | Infrastructure Baseline | infra_baseline.js | BCrypt, JWT, Redis benchmarks | Infrastructure panel |
| UC-002 | Per-API Baseline | single_api_*.js | — | API Latency panel |
| UC-003 | User Journey | chain_*.js | — | Chain Latency panel |
| UC-004 | TPS Capacity | full_load.js | — | System Overview panel |
| UC-005 | Optimization | Re-run UC-004 | Re-run relevant | Comparison panel |

---

## 4. Business Rules Summary

| ID | Rule | Category |
|----|------|----------|
| BR-001 | P95 latency < 200ms cho auth endpoints | SLA |
| BR-002 | P95 latency < 500ms cho CRUD endpoints | SLA |
| BR-003 | Error rate < 1% cho single API | SLA |
| BR-004 | Chain success rate > 99% | SLA |
| BR-005 | Target TPS ≥ 500 req/s sustained | Capacity |
| BR-006 | P99 latency < 1000ms at target TPS | Capacity |
| BR-007 | Error rate < 0.1% at target TPS | Capacity |
| BR-008 | No memory leak over 30-min test | Stability |
| BR-009 | Every optimization phải có before/after data | Process |
| BR-010 | Test phải run ≥ 30s cho JIT warmup | Test Validity |
