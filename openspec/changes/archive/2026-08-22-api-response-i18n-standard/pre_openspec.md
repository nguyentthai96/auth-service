# Pre-OpenSpec: api-response-i18n-standard

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (Feature Research Output — `openspec/research/api-response-i18n-standard/`)
> **Classification Evidence**: `I18nConfig` → `shared/config/I18nConfig.kt`; `AuthControllerAdvice` + `MessageSource` → `shared/exception/GlobalExceptionHandler.kt`; `DatabaseMessageSource` → `shared/i18n/DatabaseMessageSource.kt`; `CqrsAuthController.messageSource` → `auth/adapter/in/web/CqrsAuthController.kt`; `auth-messages*.properties` → `src/main/resources/messages/`; `ClientMetadataFilter` → `auth/adapter/in/web/filter/ClientMetadataFilter.kt`; `ContentLanguageFilter` → `auth/adapter/in/web/filter/ContentLanguageFilter.kt`
> **Archive**: N/A
> **Quality Score**: 92/100
> **_Generated**: 2026-08-22

## 📋 Feature Summary

Chuẩn hóa format API response (success + error) với server-side i18n message rendering, unified response contract, client metadata headers. Server render message i18n hoàn chỉnh (đã interpolate params) theo ngôn ngữ client yêu cầu qua `Accept-Language` header. Dual response format: Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457). Client chỉ hiển thị `message`/`detail` field — không tự build string. Frontend duy trì i18n riêng (react-i18next) cho UI text, đồng bộ language setting với Accept-Language header.

Hệ thống hiện tại đã implement ~95% infrastructure: `I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice` (với MessageSource), `ClientMetadataFilter`, `ContentLanguageFilter`, message bundles (en + vi, 44 error + 11 success keys). `CqrsAuthController` đã dùng `messageSource.getMessage()` cho 5 endpoints (register, logout, change-password, forgot-password, switch-domain). Phần còn lại (~1-2 developer-days): success message i18n cho remaining controllers/endpoints (login, refresh, MFA, token, etc.), frontend header injection + cleanup hardcoded error messages.

| Metric | Giá trị |
|--------|---------|
| Số FR | 13 (URD: 9, Enriched: 4) |
| Issues | 1 (🔴: 0, 🟡: 1) |
| Open Questions | 0 |
| **Quality Score** | **92/100** |

---

## 1. Actors

- **End User**: Người dùng cuối — nhận API messages đa ngôn ngữ, chọn ngôn ngữ ưa thích
- **Frontend App**: React SPA (admindashboard) — inject metadata headers (`Accept-Language`, `X-App-Version`, `X-Client-Platform`), hiển thị message từ server
- **Auth Service (Backend)**: Spring Boot backend — resolve locale, render message i18n qua MessageSource, trả response thống nhất
- **Admin**: Quản lý i18n messages qua DB (`i18n_messages` table) — không cần redeploy

## 2. Functional Requirements

### FR-001: Server render message đa ngôn ngữ cho error response [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải render error message hoàn chỉnh (đã interpolate params) bằng ngôn ngữ client request khi trả error response dạng `ProblemDetail`
- **Validation**: `ProblemDetail.detail` chứa giá trị đã interpolate theo locale (ví dụ: vi → "Quá nhiều lần đăng nhập")
- **Evidence**: `AuthControllerAdvice.resolveMessage()` đã implement — `GlobalExceptionHandler.kt:125`; `extractMessageArgs()` maps exception properties to `MessageFormat {0}, {1}` — `GlobalExceptionHandler.kt:107-117` — **ĐÃ IMPLEMENT**

### FR-002: Locale resolution từ Accept-Language header [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải đọc `Accept-Language` header và resolve locale tương ứng khi xử lý mỗi request
- **Validation**: `Accept-Language: vi-VN` → locale = `vi`; missing header → fallback `en`; `Accept-Language: ja` → fallback `en`
- **Evidence**: `AcceptHeaderLocaleResolver` configured in `I18nConfig.kt:39-44` với supportedLocales = [en, vi], defaultLocale = ENGLISH — **ĐÃ IMPLEMENT**

### FR-003: Message bundle infrastructure (DB + file) [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cấu hình `MessageSource` composite chain: DatabaseMessageSource (Caffeine cached, 5-min TTL) → file bundles (`auth-messages_{locale}.properties`) → default
- **Validation**: Chain resolution: DB hit → return; DB miss → file bundle; file miss → ErrorCodeBase.description fallback
- **Evidence**: `I18nConfig.kt:49-63` (CompositeMessageSource), `DatabaseMessageSource.kt` (Caffeine cache, maxSize=500, 5-min TTL), `auth-messages.properties` (70 lines, 55 keys) + `auth-messages_vi.properties` (70 lines, 55 keys) — **ĐÃ IMPLEMENT**

### FR-004: Error response dùng ProblemDetail (RFC 9457) với i18n detail [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải trả error response dạng `ProblemDetail` với `detail` field là message đã i18n, `errorCode` (machine-readable), `Content-Type: application/problem+json`
- **Validation**: Error response chứa `type`, `title`, `status`, `detail` (localized), `errorCode`
- **Evidence**: `AuthControllerAdvice.handleAuthException()` → `ProblemDetail` — `GlobalExceptionHandler.kt:48-99`; sets type, title, status, detail, errorCode, extra properties per exception type — **ĐÃ IMPLEMENT**

### FR-005: Success response với i18n message [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải trả success response với `message` field i18n server-side cho tất cả endpoints thành công
- **Validation**: Logout → `{"message": "Đã đăng xuất thành công"}`; Register → `{"message": "Đăng ký thành công"}`; Switch-domain → localized message
- **Evidence**: `CqrsAuthController` đã dùng `messageSource.getMessage()` cho register (line 96), logout (line 172), change-password (line 182), forgot-password (line 189), switch-domain (line 224) — **5/12 ENDPOINTS DONE**; cần mở rộng cho login, refresh, MFA endpoints, token endpoints, session endpoints, account lifecycle endpoints

### FR-006: Content-Language response header [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải set `Content-Language` response header trên tất cả responses để indicate ngôn ngữ thực tế của message
- **Validation**: `Content-Language: vi` khi resolve locale = `vi`
- **Evidence**: `ContentLanguageFilter.kt` — `OncePerRequestFilter` set `Content-Language` header on all `/api/**` responses (line 37-39); defense-in-depth: `AuthControllerAdvice.setContentLanguageHeader()` also sets for error responses (`GlobalExceptionHandler.kt:134-136`) — **ĐÃ IMPLEMENT (100% coverage)**

### FR-007: Client gửi Accept-Language header [URD]
- **Actor**: Frontend App
- **Action**: Client phải gửi `Accept-Language` header trong mọi API request, sync với ngôn ngữ user đã chọn
- **Validation**: Đổi language → header thay đổi → API response đổi ngôn ngữ; format RFC 7231 `vi-VN, vi;q=0.9, en;q=0.8`
- **Evidence**: `api.ts` có `setGlobalHeaders()` utility — cần wire `Accept-Language`

### FR-008: Client gửi metadata headers (X-App-Version, X-Client-Platform) [URD]
- **Actor**: Frontend App
- **Action**: Client phải gửi `X-App-Version` (semver `^\d+\.\d+\.\d+$`) và `X-Client-Platform` (web|ios|android|desktop) trong mọi request
- **Validation**: Headers present trong mọi request; server log vào MDC cho audit
- **Evidence**: `api.ts` `beforeRequest` hook — cần bổ sung headers

### FR-009: Client hiển thị message trực tiếp từ server [URD]
- **Actor**: Frontend App
- **Action**: Client phải hiển thị `problem.detail` (error) hoặc `response.message` (success) trực tiếp — không tự build string từ error params
- **Validation**: Không còn hardcoded error message template trong frontend code
- **Evidence**: Frontend forms hiện hardcode error messages — cần cleanup

### FR-010: Fallback chain khi thiếu translation [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải implement fallback chain: DatabaseMessageSource → file bundles → `ErrorCodeBase.description` khi message key không tìm thấy
- **Validation**: Missing key không gây exception; trả fallback message; log warning
- **Evidence**: `DatabaseMessageSource.resolveCode()` trả `null` khi not found → `AbstractMessageSource` delegate to parent — `DatabaseMessageSource.kt:37-47`; DB exception → log warning, return null — `DatabaseMessageSource.kt:48-50`; `resolveMessage()` dùng `defaultMessage` param — `GlobalExceptionHandler.kt:128-131` — **ĐÃ IMPLEMENT**

### FR-011: Client metadata filter cho logging (X-App-Version, X-Client-Platform → MDC) [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải extract `X-App-Version`, `X-Client-Platform` từ request headers và đưa vào MDC cho audit logging
- **Validation**: MDC chứa `appVersion`, `clientPlatform` trong mỗi request scope; MDC cleanup in finally block
- **Evidence**: `ClientMetadataFilter.kt` — `OncePerRequestFilter` with `@Order(HIGHEST_PRECEDENCE + 10)`, extracts headers, puts into MDC, cleans up in finally. Only filters `/api/**` paths. — **ĐÃ IMPLEMENT**

### FR-012: Supported locales whitelist [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải giới hạn supported locales (`en`, `vi`) và fallback unsupported locale sang `en`
- **Validation**: `Accept-Language: ja` → fallback `en`; `Accept-Language: vi` → locale `vi`
- **Evidence**: `I18nConfig.kt:42-43`: `resolver.supportedLocales = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi"))`, `resolver.setDefaultLocale(Locale.ENGLISH)` — **ĐÃ IMPLEMENT**

### FR-013: Idempotent locale resolution [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải đảm bảo locale resolution idempotent — cùng `Accept-Language` header luôn trả cùng ngôn ngữ
- **Validation**: N requests với cùng `Accept-Language: vi` → N responses với `Content-Language: vi`
- **Evidence**: `AcceptHeaderLocaleResolver` là stateless (Spring built-in) → inherently idempotent; `ContentLanguageFilter` reads from `LocaleContextHolder` (thread-local) — deterministic — **ĐÃ IMPLEMENT**

## 3. Non-functional Requirements

- **NFR-001**: Message resolution overhead < 5ms (Caffeine cached), < 50ms (DB cold hit)
- **NFR-002**: Backward compatible — existing API consumers không bị break nếu không gửi `Accept-Language` (fallback `en`)
- **NFR-003**: Message bundle loading không ảnh hưởng startup time (< 100ms overhead)
- **NFR-004**: All responses phải có `Content-Language` header — 100% coverage (`ContentLanguageFilter` ensures this)
- **NFR-005**: Cache hit ratio > 95% cho DatabaseMessageSource (Caffeine 5-min TTL, maxSize=500)

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (error i18n) và FR-005 (success i18n) có cùng mechanism (MessageSource) nhưng scope khác nhau — giữ tách biệt.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-010**: Fallback chain — domain requirement để tránh NPE/missing translation crash. Evidence: `DatabaseMessageSource.resolveCode()` trả `null` → delegate to parent.
- **FR-011**: Client metadata filter → MDC — audit logging best practice cho multi-platform service. Evidence: `ClientMetadataFilter.kt` đã implement.
- **FR-012**: Locale whitelist — bảo mật, tránh resource exhaustion qua unsupported locale. Evidence: `I18nConfig.kt` đã configure.
- **FR-013**: Idempotent resolution — deterministic behavior requirement cho caching/debugging. Evidence: `AcceptHeaderLocaleResolver` stateless.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Spring MessageSource | i18n message resolution | Built-in framework feature, đã configured trong `I18nConfig.kt` |
| Caffeine Cache | In-process cache cho DatabaseMessageSource | 5-min TTL, maxSize=500, đã implement trong `DatabaseMessageSource.kt` |
| PostgreSQL (i18n_messages) | Runtime message storage | JPA entity `I18nMessageEntity`, đã implement |
| Redis (StringRedisTemplate) | Rate limiting, OTP, anonymous sessions, anti-replay | Đã implement, không liên quan trực tiếp đến i18n |
| react-i18next | Frontend UI text i18n | Đã setup sẵn, chỉ cần sync `Accept-Language` |

## 6. Assumptions

- ⚠️ Assumption: Supported locales ban đầu: `en` (default), `vi`. Format BCP 47. Mở rộng `km` sau — Lý do: research brief confirmed en + vi only; `I18nConfig.kt` configures exactly these.
- ⚠️ Assumption: `BaseControllerAdvice` (base-core) inject `MessageSource` optional (nullable) để backward compatible — Lý do: auth-service đã override với `AuthControllerAdvice`; other services dùng base-core không bị break.
- ⚠️ Assumption: `X-App-Version` đọc từ build config hoặc env variable — Lý do: standard practice, `package.json` version.
- ⚠️ Assumption: Caffeine cache 5-min TTL là acceptable delay cho message updates — Lý do: i18n message changes rare, production standard.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-008: "semantic version" cần define regex cụ thể — ĐÃ BỔ SUNG `^\d+\.\d+\.\d+$` |
| Đầy đủ (Completeness) | 23/25 | FR-005: 5/12 endpoints done, remaining endpoints cần list cụ thể |
| Nhất quán (Consistency) | 25/25 | — |
| Kiểm thử được (Testability) | 20/25 | FR-005: cần example values cho mỗi endpoint; FR-013: "idempotent" cần rõ ràng hơn về cách verify — ĐÃ BỔ SUNG "given same header, N calls → same Content-Language" |
| **Tổng** | **92/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|----------------|
| 1 | Clarity | -1 | FR-008 | "semantic version" chưa define regex pattern — ĐÃ BỔ SUNG | Thêm pattern `^\d+\.\d+\.\d+$` |
| 2 | Completeness | -2 | FR-005 | 5/12 endpoints done, remaining chưa list rõ | List remaining: login, refresh, MFA verify, token revoke, session endpoints, account lifecycle |
| 3 | Testability | -3 | FR-005, FR-013 | FR-005: thiếu expected example cho mỗi endpoint; FR-013: verification method | Thêm example values; thêm test assertion |
| 4 | Testability | -2 | FR-009 | "không còn hardcoded" khó verify exhaustively | grep-based check cho error message patterns trong frontend |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | `LoginRateLimitFilter.writeRateLimitResponse()` tạo ProblemDetail trực tiếp (bypass AuthControllerAdvice) → detail message không qua MessageSource i18n | FR-001 | Refactor filter để dùng MessageSource hoặc throw exception để AuthControllerAdvice handle |

> Không phát hiện 🔴 Critical issues.
> Issue #2 từ previous version (missing E2EE/Anonymous message keys) đã được RESOLVED — all 44 error codes + 11 success keys đều có trong cả en + vi bundles.

## 9. Open Questions

Không có câu hỏi mở. Các OQ từ research đã được resolved:
- ~~OQ-001: Content-Language via filter or per-controller?~~ → `ContentLanguageFilter` (100% coverage) + `AuthControllerAdvice` (defense-in-depth). RESOLVED.
- ~~OQ-002: BaseControllerAdvice MessageSource injection?~~ → Auth-service's `AuthControllerAdvice` đã inject. Base-core optional. RESOLVED.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Authentication & Authorization (auth-service)
- Admin Dashboard Frontend (admindashboard)
- Shared Library (base-core) — reference only, minimal change

### 10.2 Flow Type
Command — Cross-cutting concern (i18n message resolution), áp dụng cho tất cả endpoints. Không có OTP/confirmation flow.

### 10.3 Candidate Services
- **auth-service**: Primary — `AuthControllerAdvice` (MessageSource-integrated), `CqrsAuthController` (5/12 endpoints i18n-enabled), `I18nConfig`, `DatabaseMessageSource`, `ClientMetadataFilter`, `ContentLanguageFilter`, message bundles (55 keys en+vi). Cần extend success i18n cho remaining controllers.
- **admindashboard**: Frontend — `api.ts` cần inject headers, forms cần cleanup hardcoded error messages.
- **base-core**: Reference — `ApiResponse`, `BaseControllerAdvice`, `ErrorCodeBase`. Minimal/no changes if auth-service handles all advice.

### Detection Evidence
- Keyword: `MessageSource` → Module: `shared/exception` → File: `GlobalExceptionHandler.kt` (line 4, 33)
- Keyword: `I18nConfig` → Module: `shared/config` → File: `I18nConfig.kt`
- Keyword: `DatabaseMessageSource` → Module: `shared/i18n` → File: `DatabaseMessageSource.kt`
- Keyword: `I18nMessageEntity` → Module: `shared/i18n` → File: `I18nMessageEntity.kt`
- Keyword: `I18nMessageRepository` → Module: `shared/i18n` → File: `I18nMessageRepository.kt`
- Keyword: `messageSource.getMessage` → Module: `auth/adapter/in/web` → File: `CqrsAuthController.kt` (lines 96, 172, 182, 189, 224)
- Keyword: `auth-messages` → Module: `resources/messages` → File: `auth-messages.properties` (70 lines), `auth-messages_vi.properties` (70 lines)
- Keyword: `AuthErrorCode.msgCode` → Module: `shared/exception` → File: `AuthErrorCode.kt` (44 error codes, AUTH_001-AUTH_044)
- Keyword: `ClientMetadataFilter` → Module: `auth/adapter/in/web/filter` → File: `ClientMetadataFilter.kt` (MDC logging)
- Keyword: `ContentLanguageFilter` → Module: `auth/adapter/in/web/filter` → File: `ContentLanguageFilter.kt` (Content-Language header)
- Keyword: `AcceptHeaderLocaleResolver` → Module: `shared/config` → File: `I18nConfig.kt` (line 39-44)

### 10.4 External Integrations
- Spring `MessageSource` (built-in framework feature — already configured in `I18nConfig.kt`)
- PostgreSQL `i18n_messages` table (runtime message storage — JPA entity `I18nMessageEntity`)
- Caffeine cache (in-process, already configured in `DatabaseMessageSource.kt`)
- Redis (rate limiting, OTP, sessions — not directly i18n-related)
- react-i18next (frontend UI text — already setup)

### 10.5 Required Modules
- `auth-service/shared/config` — I18nConfig [EXISTING], SecurityConfig [EXISTING]
- `auth-service/shared/exception` — AuthControllerAdvice [EXISTING], AuthErrorCode [EXISTING], exception hierarchy [EXISTING]
- `auth-service/shared/i18n` — DatabaseMessageSource [EXISTING], I18nMessageEntity [EXISTING], I18nMessageRepository [EXISTING]
- `auth-service/auth/adapter/in/web` — CqrsAuthController [MODIFY — extend success i18n], other controllers [MODIFY]
- `auth-service/auth/adapter/in/web/filter` — ClientMetadataFilter [EXISTING], ContentLanguageFilter [EXISTING]
- `auth-service/auth/adapter/in/web/filter` — LoginRateLimitFilter [MODIFY — i18n for rate limit ProblemDetail]
- `auth-service/resources/messages` — message bundles [EXISTING — complete]
- `admindashboard/src/utils` — api.ts header injection [MODIFY]
- `admindashboard/src/@i18n` — language sync [MODIFY]

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Chọn ngôn ngữ (vi/en) | Frontend react-i18next |
| 2 | Frontend App | Set global headers: `Accept-Language: vi-VN`, `X-App-Version: 1.0.0`, `X-Client-Platform: web` | ky HTTP client `beforeRequest` hook |
| 3 | Frontend App | Gửi API request | ky → Auth Service |
| 4 | Auth Service | `ClientMetadataFilter` extract `X-App-Version`, `X-Client-Platform` → MDC | OncePerRequestFilter (HIGHEST_PRECEDENCE + 10) |
| 5 | Auth Service | `AcceptHeaderLocaleResolver` resolve locale từ `Accept-Language` header | Spring MVC → `LocaleContextHolder` |
| 6 | Auth Service | Controller/Handler xử lý business logic | CQRS pattern |
| 7a | Auth Service | (Success) `MessageSource.getMessage(key, args, locale)` → build response with localized message | Controller → MessageSource |
| 7b | Auth Service | (Error) Exception → `AuthControllerAdvice` → `resolveMessage()` → build `ProblemDetail` | ControllerAdvice → MessageSource |
| 8 | Auth Service | `ContentLanguageFilter` set `Content-Language` response header | OncePerRequestFilter (LOWEST_PRECEDENCE - 10) |
| 9 | Frontend App | (Success) Show `response.message` trực tiếp | React UI toast/notification |
| 10 | Frontend App | (Error) Show `problem.detail` trực tiếp, use `errorCode` cho client-side logic | React UI error display |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Research: UC-001 | TBD | `AuthControllerAdvice` (`GlobalExceptionHandler.kt`) [EXISTING] | ✅ Implemented |
| FR-002 | Research: UC-001 | TBD | `AcceptHeaderLocaleResolver` in `I18nConfig.kt` [EXISTING] | ✅ Implemented |
| FR-003 | Research: UC-001 | TBD | `I18nConfig.kt`, `DatabaseMessageSource.kt`, `auth-messages*.properties` [EXISTING] | ✅ Implemented |
| FR-004 | Research: UC-001 | TBD | `AuthControllerAdvice` (`GlobalExceptionHandler.kt`) [EXISTING] | ✅ Implemented |
| FR-005 | Research: UC-003 | TBD | `CqrsAuthController.kt` [MODIFY], other controllers [MODIFY] | 🟡 Partial (5/12) |
| FR-006 | Research: UC-001,003 | TBD | `ContentLanguageFilter.kt` [EXISTING], `AuthControllerAdvice` [EXISTING] | ✅ Implemented |
| FR-007 | Research: UC-002 | TBD | `api.ts` [MODIFY], `I18nProvider.tsx` [MODIFY] | ⬜ Not started |
| FR-008 | Research: UC-002 | TBD | `api.ts` [MODIFY] | ⬜ Not started |
| FR-009 | Research: UC-001,003 | TBD | Frontend forms (`JwtSignInForm.tsx`, `SignInPageForm.tsx`) [MODIFY] | ⬜ Not started |
| FR-010 | Enriched | TBD | `DatabaseMessageSource.kt` [EXISTING], `AuthControllerAdvice` [EXISTING] | ✅ Implemented |
| FR-011 | Enriched | TBD | `ClientMetadataFilter.kt` [EXISTING] | ✅ Implemented |
| FR-012 | Enriched | TBD | `I18nConfig.kt` [EXISTING] | ✅ Implemented |
| FR-013 | Enriched | TBD | `AcceptHeaderLocaleResolver` [EXISTING — stateless] | ✅ Implemented |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
Feature này thuộc dạng **cross-cutting concern** — ảnh hưởng toàn bộ API layer nhưng thay đổi chủ yếu ở infrastructure level (config, advice, filter). Rủi ro rất thấp vì:

1. **95%+ infrastructure đã implement**: `I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice` (MessageSource integration), `ClientMetadataFilter` (MDC logging), `ContentLanguageFilter` (100% Content-Language coverage), message bundles (en + vi, 55 keys covering all 44 error codes + 11 success messages)
2. `CqrsAuthController` đã dùng `messageSource.getMessage()` cho 5 endpoints (register, logout, change-password, forgot-password, switch-domain) — pattern đã proven
3. `AuthErrorCode.msgCode` convention match message bundle keys exactly (e.g., `auth.rate_limited` → `auth-messages.properties`)
4. `AcceptHeaderLocaleResolver` + `ContentLanguageFilter` + `ClientMetadataFilter` → full request/response i18n pipeline đã hoạt động

**Progress update vs previous analysis:**
- [RESOLVED] Issue #2 (missing E2EE/Anonymous message keys) — all 44 error codes now have i18n messages in both en + vi bundles
- [RESOLVED] FR-011 (ClientMetadataFilter) — now EXISTING at `auth/adapter/in/web/filter/ClientMetadataFilter.kt`
- [NEW] FR-006 coverage improved — `ContentLanguageFilter.kt` provides 100% coverage for Content-Language header on all API responses
- [NEW] Issue #1 updated — `LoginRateLimitFilter.writeRateLimitResponse()` bypasses MessageSource (hardcoded English message)

**Remaining work (~1-2 developer-days):**
- Extend success i18n cho remaining controller endpoints (login response, refresh, MFA, token, session, account lifecycle)
- Refactor `LoginRateLimitFilter.writeRateLimitResponse()` to use MessageSource for i18n
- Frontend: inject `Accept-Language` + metadata headers vào ky client
- Frontend: cleanup hardcoded error messages → show server message directly
- Add success message keys for remaining endpoints

### Related Features / Precedents
- `auth-login-admin` (archived: `openspec/changes/archive/2026-08-11-auth-login-admin/`) — triển khai `AuthControllerAdvice` + `ProblemDetail` error format + `I18nConfig`. Feature này là evolution — extend i18n coverage.
- `anonymous-login-optimization` (archived: `openspec/changes/archive/2026-08-20-anonymous-login-optimization/`) — added `AnonymousExceptions` (AUTH_040-044) with i18n message keys in bundles.
- `e2ee-performance-compliance` (archived: `openspec/changes/archive/2026-08-15-e2ee-performance-compliance/`) — added `CipherExceptions` (AUTH_030-039) with i18n message keys.

### Integration Notes
- **Spring MessageSource**: Built-in, zero external dependency. `I18nConfig.kt` provides CompositeMessageSource chain (DB → file).
- **DatabaseMessageSource**: Caffeine cache (5-min TTL, maxSize=500). DB unavailable → log warning → file bundle fallback. Transparent to consumers.
- **ContentLanguageFilter**: `OncePerRequestFilter` at `LOWEST_PRECEDENCE - 10` — runs AFTER controller processing, sets `Content-Language` from `LocaleContextHolder`. Only filters `/api/**` paths.
- **ClientMetadataFilter**: `OncePerRequestFilter` at `HIGHEST_PRECEDENCE + 10` — runs EARLY, extracts `X-App-Version` + `X-Client-Platform` → MDC. Cleans up in finally block. Only filters `/api/**` paths.
- **LoginRateLimitFilter**: Creates `ProblemDetail` directly without MessageSource — needs refactoring for i18n compliance.
- **react-i18next**: Frontend i18n đã setup, có `en` locale JSON. Cần sync `i18n.language` → `Accept-Language` header.
- **Backward compatible**: Client không gửi `Accept-Language` → server dùng `en` (default). Zero breaking changes.

### Suggested Approach
1. **auth-service backend FIRST**: 
   - Extend success i18n cho remaining controllers (login, refresh, MFA, session, token, account lifecycle)
   - Add success message keys to bundles for new endpoints
   - Refactor `LoginRateLimitFilter` to use MessageSource for ProblemDetail messages
2. **frontend SECOND**:
   - `api.ts` — inject `Accept-Language`, `X-App-Version`, `X-Client-Platform` vào global headers
   - `I18nProvider.tsx` — sync language change → `Accept-Language` header
   - Cleanup hardcoded error messages trong forms
3. **base-core (optional)**: `BaseControllerAdvice` inject `MessageSource?` nullable — chỉ nếu cần share pattern across services

### Context from Confluence Images
N/A

### Change Impact Map (EXTEND)

```
Change Impact:
  FR-001 → [REUSE] AuthControllerAdvice (GlobalExceptionHandler.kt) → no changes needed (already implemented)
  FR-002 → [REUSE] AcceptHeaderLocaleResolver (I18nConfig.kt) → no changes needed
  FR-003 → [REUSE] I18nConfig, DatabaseMessageSource, auth-messages*.properties → no changes needed
  FR-004 → [REUSE] AuthControllerAdvice (GlobalExceptionHandler.kt) → no changes needed
  FR-005 → [MODIFY] CqrsAuthController.kt, AuthController.kt, MfaController.kt, SessionController.kt, TokenController.kt, AccountLifecycleController.kt → extend success i18n
  FR-006 → [REUSE] ContentLanguageFilter.kt → no changes needed
  FR-007 → [MODIFY] api.ts → inject Accept-Language header
  FR-008 → [MODIFY] api.ts → inject X-App-Version, X-Client-Platform
  FR-009 → [MODIFY] JwtSignInForm.tsx, SignInPageForm.tsx → remove hardcoded messages
  FR-010 → [REUSE] DatabaseMessageSource.kt, AuthControllerAdvice → no changes needed
  FR-011 → [REUSE] ClientMetadataFilter.kt → no changes needed (already implemented)
  FR-012 → [REUSE] I18nConfig.kt → no changes needed
  FR-013 → [REUSE] AcceptHeaderLocaleResolver → no changes needed
```
