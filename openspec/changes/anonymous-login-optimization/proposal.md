## Why

[CHANGED] Auth-service đã có anonymous login feature FULLY IMPLEMENTED (12 classes ADD + 10 classes MODIFY). Tuy nhiên, Phase B code scan phát hiện các vấn đề cần khắc phục trước khi production-ready:

1. **🔴 BUG — JTI Placeholder**: `LoginHandler` (L160) và `RegisterHandler` (L94) truyền `anonymousJti = ""` vào `SessionPromotionService`, dẫn đến anonymous token **KHÔNG được blacklist** sau promotion. Token có thể tái sử dụng — vi phạm FR-005.
2. **🔴 GAP — Zero Test Coverage**: Toàn bộ anonymous feature (12 classes mới + 10 classes sửa) không có test. Rủi ro regression cao cho feature security-critical.
3. **🟡 SMELL — ThreadLocal in RegisterHandler**: Dùng `ThreadLocal<PromotionResult?>` để truyền promotion result từ handler → controller. Fragile với pooled/virtual threads, violates SRP.
4. **🟡 GAP — No Observability**: Không có Micrometer metrics cho anonymous session lifecycle. Operations team không thể monitor, detect abuse, hay alert resource exhaustion.

## Changes

[CHANGED] Scope reassessed from EXTEND → **MAINTENANCE** (post-implementation hardening).

- **FIX-001: JTI Blacklisting Fix** — Add `anonymousToken: String?` field to `LoginRequestDto`/`RegisterRequestDto`. Add `anonymousTokenJti: String?` to `LoginCommand`/`RegisterCommand`. Controller extracts JTI via `jwtService.parseAnonymousToken()`. Handlers pass real JTI to `SessionPromotionService`. Remove empty-string placeholder.
- **FIX-002: Remove ThreadLocal from RegisterHandler** — Create `RegisterResult` sealed class (mirrors `LoginResult`). `RegisterHandler` returns `RegisterResult` instead of `AuthToken`. Update `CqrsAuthController.register()` to unwrap `RegisterResult`. Remove ThreadLocal + `lastPromotionResult`.
- **FIX-003: Add Observability Metrics** — Inject `MeterRegistry` into all 5 anonymous service classes. Add Micrometer counters: sessions created/renewed/promoted/rate-limited/data-stored/data-exceeded. Add timers: promotion duration, token generation duration.
- **TEST-001: Integration Test Suite** — Create `AnonymousSessionIntegrationTest.kt`, `SessionPromotionIntegrationTest.kt`. Cover: create session, rate limiting, store/read/delete data, renew token, promotion on login/register, concurrent promotion, expired session, token reuse after blacklisting.
- **TEST-002: Unit Test Suite** — Create `JwtServiceAnonymousTest.kt`, `AnonymousSessionDataServiceTest.kt`, `SessionPromotionServiceTest.kt`, `AnonymousRateLimitServiceTest.kt`.

## Capabilities

### Fixed Capabilities
- `session-promotion-jti-blacklisting`: Anonymous token JTI correctly blacklisted after promotion (FIX-001)
- `register-handler-thread-safety`: RegisterHandler returns composite result type instead of ThreadLocal (FIX-002)

### New Capabilities
- `anonymous-observability`: Micrometer counters and timers for anonymous session lifecycle (FIX-003)
- `anonymous-test-coverage`: Integration + unit test suites for entire anonymous feature (TEST-001, TEST-002)

### Unchanged Capabilities
- `anonymous-session-creation` — no changes needed (working correctly)
- `anonymous-session-data` — no changes needed (working correctly)
- `anonymous-token-renewal` — no changes needed (working correctly)
- `anonymous-rate-limiting` — no changes needed (working correctly, metrics added)
- `anonymous-security-role` — no changes needed (working correctly)
- `promotion-distributed-lock` — no changes needed (working correctly)

## Impact

### Backend (auth-service)

**MODIFY** (11 existing files):
- `LoginHandler.kt` — replace `anonymousJti = ""` with `command.anonymousTokenJti` (~2 lines changed)
- `RegisterHandler.kt` — replace `anonymousJti = ""` + remove ThreadLocal + change return type (~20 lines changed)
- `LoginCommand.kt` — add `anonymousTokenJti: String? = null` field (~1 line)
- `RegisterCommand.kt` — add `anonymousTokenJti: String? = null` field (~1 line)
- `CqrsAuthController.kt` — extract JTI from token, pass to commands, handle RegisterResult (~15 lines)
- `RequestDtos.kt` — add `anonymousToken: String? = null` to LoginRequestDto + RegisterRequestDto (~2 lines)
- `AnonymousSessionHandler.kt` — inject MeterRegistry, add counter (~5 lines)
- `RenewAnonymousTokenHandler.kt` — inject MeterRegistry, add counter (~5 lines)
- `SessionPromotionService.kt` — inject MeterRegistry, add counter + timer (~10 lines)
- `AnonymousRateLimitService.kt` — inject MeterRegistry, add counter (~5 lines)
- `AnonymousSessionDataService.kt` — inject MeterRegistry, add counters (~8 lines)

**NEW** (7 files):
- `RegisterResult.kt` — sealed class for register handler return type
- `AnonymousSessionIntegrationTest.kt` — integration tests
- `SessionPromotionIntegrationTest.kt` — integration tests
- `AnonymousRateLimitServiceTest.kt` — unit tests
- `JwtServiceAnonymousTest.kt` — unit tests
- `AnonymousSessionDataServiceTest.kt` — unit tests
- `SessionPromotionServiceTest.kt` — unit tests

### Database
- **No changes** — no new tables, no migrations

### External Systems
- **No changes** — same Redis key namespaces, same PostgreSQL table usage
