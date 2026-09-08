# Service Structure

_Generated: 2026-09-08_

## auth-service

### Detected Packages

- `auth/adapter/in/web/` — REST Controllers (14 controllers)
- `auth/adapter/in/web/dto/` — Request/Response DTOs
- `auth/adapter/in/web/filter/` — HTTP Filters
- `auth/adapter/in/kafka/` — Kafka consumers
- `auth/adapter/out/http/` — HTTP clients (SSO, Captcha)
- `auth/adapter/out/cache/` — Cache adapters
- `auth/adapter/out/cipher/` — Encryption adapters
- `auth/adapter/out/event/` — Event adapters
- `auth/adapter/out/gateway/` — Gateway implementations
- `auth/adapter/out/notification/` — Notification adapters
- `auth/adapter/out/persistence/entity/` — JPA entities
- `auth/adapter/out/persistence/mapper/` — Entity mappers
- `auth/adapter/out/persistence/repository/` — JPA repositories
- `auth/adapter/out/sso/` — SSO adapters
- `auth/application/` — Application services
- `auth/application/cipher/` — Cipher services
- `auth/application/command/` — CQRS command handlers
- `auth/application/event/` — Event handlers
- `auth/application/port/out/` — Output port interfaces
- `auth/application/query/` — CQRS query handlers
- `auth/domain/model/` — Domain models
- `auth/domain/model/vo/` — Value objects
- `auth/domain/service/` — Domain services
- `auth/domain/event/` — Domain events
- `rbac/adapter/in/web/` — RBAC controllers
- `pbac/adapter/in/web/` — Policy controllers
- `shared/config/` — Shared configuration (Redis, HTTP)
- `shared/exception/` — Exception classes
- `shared/filter/` — Shared filters

### Test Structure (Performance-relevant)

- `tests/load/` — K6 load test scripts (4 files: auth_flow.js, profile_flow.js, cache_benchmark.js, helpers.js)
- `src/jmh/kotlin/` — JMH microbenchmarks (2 files: EncryptionBenchmark.kt, SerializationBenchmark.kt)

### Configuration Files (Performance-relevant)

- `src/main/resources/application-db.yml` — Database config (NO HikariCP explicit config)
- `src/main/resources/application-core-observability.yml` — Actuator exposure (health,info,metrics — NO prometheus)
- `compose.yaml` — Docker Compose (postgres + redis only — NO prometheus/grafana)
- `build.gradle.kts` — JMH plugin, K6 Gradle tasks, datasource-proxy dependency

### Not Found

- `tests/perf/` — Performance test directory (needs creation)
- Prometheus configuration files
- Grafana provisioning files

### Naming Convention

- Controllers: `*Controller.kt` (PascalCase)
- DTOs: `*Request`, `*Response`, `*Dto` (PascalCase)
- Services: `*Service.kt` (PascalCase)
- Config: `*Config.kt` (PascalCase)
- K6 scripts: `snake_case.js`
- JMH: `*Benchmark.kt` (PascalCase)
