# Proposal: tps-performance-testing

> **Change**: tps-performance-testing | **Type**: EXTEND | **Flow**: Command
> **Direction**: 4-Phase Hybrid — datasource-proxy Foundation → Integration Tests (WireMock + Cache) → K6 Expansion → JMH + CI/CD Gate (from brainstorm — Approach 2)
> **Previous Iteration**: N/A (first iteration)
> **_Generated**: 2026-08-27_

## Changes

- **base-testing-starter [NEW]**: `DataSourceProxyConfig.kt` — @TestConfiguration wrapping DataSource with datasource-proxy for SQL query counting in integration tests. (FR-001, FR-009)
- **base-testing-starter [NEW]**: `QueryCountAssertions.kt` — Kotlin DSL object providing `assertQueryCount(select=N) { block }` inline function for asserting SQL query counts. (FR-001)
- **base-testing-starter [NEW]**: `AssertQueryCount.kt` — JUnit 5 annotation `@AssertQueryCount(select=2)` for declarative query count assertion. (FR-001)
- **base-testing-starter [NEW]**: `AssertQueryCountExtension.kt` — JUnit 5 Extension (BeforeEachCallback + AfterEachCallback) wiring `QueryCountHolder` reset/assert logic. (FR-001, FR-008)
- **auth-service/src/test [NEW]**: `QueryCountIntegrationTest.kt` — Integration tests using `assertQueryCount` DSL to verify N+1 detection on auth-service JPA entities (login, token refresh). (FR-008)
- **auth-service/src/test [NEW]**: `WireMockExternalServiceTest.kt` — Integration tests with `@AutoConfigureWireMock(port = 0)` simulating SSO/Captcha latency and circuit breaker activation. (FR-002)
- **auth-service/src/test [MODIFY]**: `CacheEncryptionIntegrationTest.kt` — Extended to verify Redis raw data format for NONE/FULL/PARTIAL encryption modes via `StringRedisTemplate`. (FR-007)
- **auth-service/tests/load [MODIFY]**: `auth_flow.js` — Expanded from 50 VUs/10s single scenario to 500 VUs ramping-vus + constant-arrival-rate dual scenario with multi-threshold. (FR-003, FR-006)
- **auth-service/tests/load [MODIFY]**: `profile_flow.js` — Expanded with constant-arrival-rate 100 req/s, dynamic token acquisition from login response. (FR-003)
- **auth-service/tests/load [NEW]**: `cache_benchmark.js` — K6 script benchmarking TPS across cache encryption modes (NONE/FULL/PARTIAL) via env variable. (FR-010)
- **auth-service/tests/load [NEW]**: `helpers.js` — Shared K6 utility module exporting `loginAndGetToken()` for token-dependent scenarios. (FR-003, FR-010)
- **auth-service [MODIFY]**: `build.gradle.kts` — Add `me.champeau.jmh` plugin, `datasource-proxy` testImplementation, `spring-cloud-contract-wiremock` testImplementation, `k6CacheBenchmark` Gradle Exec task, JMH configuration block. (FR-004, FR-005, FR-009)
- **auth-service/src/jmh [NEW]**: `EncryptionBenchmark.kt` — JMH benchmark measuring AES-GCM encryption/decryption throughput (ops/sec). (FR-005)
- **auth-service/src/jmh [NEW]**: `SerializationBenchmark.kt` — JMH benchmark measuring Jackson serialization/deserialization overhead (ops/sec). (FR-005)

**Total**: 2 production config files modified (build.gradle.kts in auth-service + base-testing-starter), 1 existing test extended, 4 new test files in base-testing-starter, 3 new test files in auth-service, 4 new/modified K6 scripts, 2 new JMH benchmark files.

---

## 1. Executive Summary

This proposal establishes a comprehensive performance testing infrastructure for auth-service across 4 layers:

1. **Micro-level (JMH)**: Benchmark AES-GCM encryption and Jackson serialization overhead in isolated JVM forks — no Spring context noise.
2. **I/O Integration (datasource-proxy + WireMock)**: Detect N+1 SQL queries automatically via `assertQueryCount` DSL; simulate external HTTP service latency/fault with WireMock to verify circuit breaker behavior.
3. **Cache Validation (Testcontainers)**: Verify Redis raw data format for each encryption mode (NONE/FULL/PARTIAL) ensuring data-at-rest encryption correctness.
4. **E2E Load (K6)**: Expand K6 scripts from 50 VUs to 500 VUs with multi-scenario execution (ramping-vus + constant-arrival-rate), enforce CI/CD performance gates via K6 thresholds.

All changes are **test-scoped only** — zero production code modifications. The `assertQueryCount` infrastructure is placed in `base-testing-starter` for cross-service reuse.

## 2. Scope

| Dimension | Value |
|-----------|-------|
| Feature Type | EXTEND |
| Flow | Command (test execution → pass/fail report) |
| Services | auth-service (primary), base-testing-starter (shared utility) |
| FR Count | 10 (URD: 7, Enriched: 3) |
| New Dependencies | datasource-proxy, spring-cloud-contract-wiremock, me.champeau.jmh, jmh-core |
| Production Impact | None (test-scoped only) |
| Estimated Effort | 5-8 dev-days across 4 phases |

## 3. Motivation

| Gap | Current State | Target State |
|-----|--------------|--------------|
| N+1 Detection | Manual log review | Automated `assertQueryCount` DSL + `@AssertQueryCount` annotation |
| External Service Testing | No HTTP mock | WireMock latency/fault simulation for SSO + Captcha |
| Load Testing | 50 VUs / 10s (basic) | 500 VUs / 30s (multi-scenario, CI/CD gate) |
| Encryption Benchmarking | None | JMH microbenchmarks for AES-GCM + Jackson |
| Cache Correctness | Basic set/get test only | NONE/FULL/PARTIAL mode raw data verification |
| CI/CD Performance Gate | None | K6 thresholds → exit code 99 → pipeline fail |

## 4. Phased Approach (from brainstorm — Approach 2)

### Phase 1 — Foundation (datasource-proxy in base-testing-starter)
**FRs**: FR-001, FR-009
**Deliverables**: `DataSourceProxyConfig`, `QueryCountAssertions` DSL, `@AssertQueryCount` annotation, `AssertQueryCountExtension`
**Validation**: Unit test with H2 DataSource

### Phase 2 — Integration Tests (auth-service)
**FRs**: FR-002, FR-007, FR-008
**Deliverables**: `QueryCountIntegrationTest`, `WireMockExternalServiceTest`, enhanced `CacheEncryptionIntegrationTest`
**Validation**: All tests green with H2 + Redis Testcontainer + WireMock

### Phase 3 — K6 Expansion
**FRs**: FR-003, FR-004, FR-010
**Deliverables**: Enhanced `auth_flow.js`, enhanced `profile_flow.js`, new `cache_benchmark.js`, `helpers.js`, `k6CacheBenchmark` Gradle task
**Validation**: K6 scripts execute successfully against running auth-service

### Phase 4 — JMH + CI/CD Gate
**FRs**: FR-005, FR-006
**Deliverables**: `EncryptionBenchmark.kt`, `SerializationBenchmark.kt`, JMH plugin configuration, CI/CD threshold verification
**Validation**: `./gradlew jmh` produces results JSON, K6 thresholds enforce exit code 99

## 5. Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| datasource-proxy conflicts with HikariCP in test context | LOW | MEDIUM | Use `@TestConfiguration` (not BeanPostProcessor). Test with H2 first. |
| WireMock port conflicts in parallel test execution | MEDIUM | LOW | `@AutoConfigureWireMock(port = 0)` with dynamic port injection. |
| JMH plugin incompatible with JVM 25 | LOW | LOW | Fallback: use JMH jar directly, pin compatible version. |
| K6 Docker container network issues on CI | LOW | HIGH | `--network host` already proven. Document CI Docker requirements. |
| base-testing-starter change impacts other services | LOW | MEDIUM | Additive-only changes (new classes, no modification to existing code). |
| K6 500 VUs overwhelms local dev machine | MEDIUM | LOW | Document minimum hardware. Provide `k6RunLite` task with 50 VUs for local. |

## 6. Open Questions

- ⚠️ OPEN QUESTION: `base-testing-starter` build system — does it use same Gradle convention plugin? Need to verify dependency management for datasource-proxy.
- ⚠️ OPEN QUESTION: Should K6 `helpers.js` export a shared `loginAndGetToken()` function, or should each script handle auth independently?
- ⚠️ OPEN QUESTION: JMH benchmark — should we benchmark `base-cache-starter`'s encryption utilities directly, or create simplified test doubles?

## 7. FR Coverage Matrix

| FR-ID | Phase | Description | Artifact Impact |
|-------|-------|-------------|----------------|
| FR-001 | 1 | assertQueryCount DSL | base-testing-starter (4 new files) |
| FR-002 | 2 | WireMock HTTP simulation | auth-service (1 new test) |
| FR-003 | 3 | K6 E2E expansion | auth-service (3 scripts modified/new) |
| FR-004 | 3 | K6 Gradle integration | auth-service (build.gradle.kts) |
| FR-005 | 4 | JMH microbenchmark | auth-service (2 new benchmarks + plugin) |
| FR-006 | 4 | CI/CD performance gate | auth-service (K6 thresholds + exit codes) |
| FR-007 | 2 | Cache encryption correctness | auth-service (1 test extended) |
| FR-008 | 2 | N+1 query detection | auth-service (1 new test) |
| FR-009 | 1 | Datasource-proxy config | base-testing-starter (1 new file) |
| FR-010 | 3 | Cache benchmark K6 script | auth-service (1 new script + Gradle task) |
