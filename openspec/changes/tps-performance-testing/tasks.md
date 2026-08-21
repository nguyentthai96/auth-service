<!-- self-contained: true -->
# Implementation Tasks

## Phase 1: Infrastructure (base-testing-starter)
- [ ] **Task 1: Setup JMH & Datasource Proxy**
  - File: `base-testing-starter/build.gradle.kts` | Action: [MODIFY]
  - Base: `N/A`
  - FR: FR-001, FR-005
  - Pattern: Thêm `net.ttddyy:datasource-proxy` và plugin JMH.
- [ ] **Task 2: Implement AssertQueryCount Utility**
  - File: `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/db/AssertQueryCount.kt` | Action: [NEW]
  - Base: `N/A`
  - FR: FR-001
  - Dependencies: Datasource Proxy QueryExecutionListener.

## Phase 2: Integration Tests (auth-service)
- [ ] **Task 3: Write Cache Encryption Correctness Test**
  - File: `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt` | Action: [NEW]
  - Base: `@SpringBootTest`
  - FR: FR-007
  - Pattern: Inject `StringRedisTemplate`, dùng MockMvc gọi API Login/Profile, sau đó query Redis để assert format chuỗi (JSON vs Encrypted payload).

## Phase 3: E2E Load Test Pipeline
- [ ] **Task 4: Write K6 Auth Flow (Login)**
  - File: `auth-service/tests/load/auth_flow.js` | Action: [NEW]
  - Base: K6 script
  - FR: FR-003, FR-006
  - Pattern: Define `export let options = { vus: 500, duration: '30s', thresholds: { 'http_req_duration': ['p(95)<200'] } }`.
- [ ] **Task 5: Write K6 Profile Flow (Get Me)**
  - File: `auth-service/tests/load/profile_flow.js` | Action: [NEW]
  - Base: K6 script
  - FR: FR-003, FR-006
- [ ] **Task 6: Configure K6 Gradle Exec Task**
  - File: `auth-service/build.gradle.kts` | Action: [MODIFY]
  - Base: `Exec` task
  - FR: FR-004
  - Pattern: Đăng ký task `k6Run` gọi command `docker run --rm -i grafana/k6 run - < tests/load/auth_flow.js`.
