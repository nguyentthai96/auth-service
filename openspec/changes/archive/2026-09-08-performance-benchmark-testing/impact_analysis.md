# Impact Analysis: performance-benchmark-testing

## 1. Core Files Affected

| File | Action | FR | Impact Level |
|------|--------|-----|-------------|
| `src/main/resources/application-core-observability.yml` | MODIFY | FR-001, FR-018, FR-021 | 🟢 Low |
| `src/main/resources/application-db.yml` | MODIFY | FR-003 | 🟢 Low |
| `compose-perf.yaml` | NEW | FR-002 | 🟢 None (new file) |
| `tests/perf/**` | NEW | FR-005→009, FR-013→017, FR-019 | 🟢 None (new dir) |
| `src/jmh/kotlin/**/BCryptBenchmark.kt` | NEW | FR-010 | 🟢 None |
| `src/jmh/kotlin/**/JwtBenchmark.kt` | NEW | FR-011 | 🟢 None |
| `src/jmh/kotlin/**/RedisCacheBenchmark.kt` | NEW | FR-012 | 🟢 None |
| `build.gradle.kts` | MODIFY | Tasks | 🟢 Low |

## 2. Call Tree (for MODIFY files)

### `application-core-observability.yml`
```
management.endpoints.web.exposure.include
├── Currently: health, info, metrics
├── Adding: prometheus
├── Consumers: Spring Boot Actuator autoconfigure
└── Impact: Additional endpoint exposed — no existing behavior changed

management.metrics.distribution (NEW section)
├── Consumers: Micrometer → Prometheus
└── Impact: Additional histogram buckets exported — additive only

server.tomcat.accesslog (NEW section)
├── Consumers: Embedded Tomcat
└── Impact: New log file created — no existing log changed
```

### `application-db.yml`
```
spring.datasource.hikari (NEW section)
├── Currently: Uses HikariCP defaults (pool-size=10, min-idle=10)
├── Adding: Explicit config with same defaults via env vars
├── Consumers: HikariCP → DataSource → JPA repositories
└── Impact: ❌ ZERO behavioral change — env vars default to same values
```

### `build.gradle.kts`
```
New Gradle tasks (additive)
├── k6Perf* tasks → new tasks, no conflict with existing k6Run
├── No existing task modified
└── Impact: 🟢 None — purely additive
```

## 3. Blast Radius

| Depth | Count | Details |
|-------|-------|---------|
| d=0 (direct) | 3 files modified | observability.yml, db.yml, build.gradle.kts |
| d=1 (consumers) | Spring Boot autoconfigure | Additive config only |
| d=2 (transitive) | None | No production code depends on new config keys |

**Overall Risk: 🟢 LOW** — All MODIFY operations are additive (new YAML keys, new Gradle tasks). No existing keys/values changed. New files have zero blast radius.

## 4. Reuse Map

| FR | Reuse Candidate | Match % | Decision |
|----|----------------|---------|----------|
| FR-005 (K6 single API) | `tests/load/helpers.js` | 70% | REUSE — import `loginAndGetToken`, `BASE_URL`, `DEFAULT_HEADERS` |
| FR-006 (K6 chain) | `tests/load/auth_flow.js` | 50% | PARTIAL — reuse scenario pattern, new chain logic |
| FR-010 (BCrypt JMH) | `src/jmh/.../EncryptionBenchmark.kt` | 40% | NEW — different algorithm, but follow same benchmark structure |
| FR-011 (JWT JMH) | `src/jmh/.../EncryptionBenchmark.kt` | 60% | EXTRACT — reuse `@State`, `@BenchmarkMode`, `@OutputTimeUnit` setup pattern |
| FR-012 (Redis JMH) | `src/jmh/.../SerializationBenchmark.kt` | 80% | REUSE — extend existing serializer benchmark with Redis-specific payloads |

> No HARD BLOCK situations (no ≥80% match requiring mandatory EXTRACT from shared classes).
> FR-012 is 80% match but extends existing JMH test in same domain — REUSE is appropriate.

## 5. Context Snapshot

### Existing Test Infrastructure (from archive)
- K6: 4 scripts in `tests/load/` (auth_flow.js, profile_flow.js, cache_benchmark.js, helpers.js)
- JMH: 2 benchmarks in `src/jmh/kotlin/` (EncryptionBenchmark.kt, SerializationBenchmark.kt)
- Gradle: 3 K6 tasks (k6Run, k6ProfileRun, k6CacheBenchmark), JMH config (fork=2, warmup=5, iter=5)
- Docker: compose.yaml (postgres + redis only)
- Observability: actuator expose health,info,metrics (no prometheus)

### Key Dependencies
- `base-observability-starter`: provides `micrometer-registry-prometheus` — NO new dependency needed
- `spring-boot-starter-actuator`: already in build.gradle.kts:19
- `me.champeau.jmh` plugin v0.7.2: already configured
- `grafana/k6` Docker image: already used in existing tasks

### Production Code Safety
- ⚠️ `application-core-observability.yml` is loaded by ALL Spring profiles
  - Adding `prometheus` to exposure.include → new endpoint exposed in ALL environments
  - **Mitigation**: Acceptable for observability. Prometheus endpoint is read-only, no side effects.
- ⚠️ `application-db.yml` HikariCP config changes defaults
  - **Mitigation**: Using `${DB_POOL_MAX:10}` — default identical to current behavior. Only changes when env var explicitly set.
- ✅ All other files are NEW — zero risk to existing functionality
