# Pre-OpenSpec: api-response-i18n-standard

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (Feature Research Output — `openspec/research/api-response-i18n-standard/`)
> **Classification Evidence**: `I18nConfig` → `shared/config/I18nConfig.kt`; `AuthControllerAdvice` + `MessageSource` → `shared/exception/GlobalExceptionHandler.kt`; `DatabaseMessageSource` → `shared/i18n/DatabaseMessageSource.kt`; `CqrsAuthController.messageSource` → `auth/adapter/in/web/CqrsAuthController.kt`; `auth-messages*.properties` → `src/main/resources/messages/`
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Chuẩn hóa format API response (success + error) với server-side i18n message rendering, unified response contract, client metadata headers. Server render message i18n hoàn chỉnh (đã interpolate params) theo ngôn ngữ client yêu cầu qua `Accept-Language` header. Dual response format: Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457). Client chỉ hiển thị `message`/`detail` field — không tự build string. Frontend duy trì i18n riêng (react-i18next) cho UI text, đồng bộ language setting với Accept-Language header.

Hệ thống hiện tại đã implement ~90% infrastructure: `I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice` (với MessageSource), message bundles (en + vi, 44 keys). Phần còn lại (~3 developer-days): success message i18n cho tất cả controllers, client metadata headers, cleanup hardcoded error messages trên frontend.

| Metric | Giá trị |
|--------|---------|
| Số FR | 13 (URD: 9, Enriched: 4) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 0 |
| **Quality Score** | **88/100** |

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
- **Validation**: `ProblemDetail.detail` chứa giá trị đã interpolate theo locale (ví dụ: vi → "Quá nhiều lần đăng nhập (IP). Vui lòng đợi.")
- **Evidence**: `AuthControllerAdvice.resolveMessage()` đã implement — `GlobalExceptionHandler.kt:125`

### FR-002: Locale resolution từ Accept-Language header [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải đọc `Accept-Language` header và resolve locale tương ứng khi xử lý mỗi request
- **Validation**: `Accept-Language: vi-VN` → locale = `vi`; missing header → fallback `en`; `Accept-Language: ja` → fallback `en`
- **Evidence**: Spring `AcceptHeaderLocaleResolver` (built-in); `LocaleContextHolder.getLocale()` used in `GlobalExceptionHandler.kt`

### FR-003: Message bundle infrastructure (DB + file) [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cấu hình `MessageSource` composite chain: DatabaseMessageSource (Caffeine cached, 5-min TTL) → file bundles (`auth-messages_{locale}.properties`) → default
- **Validation**: Chain resolution: DB hit → return; DB miss → file bundle; file miss → ErrorCodeBase.description fallback
- **Evidence**: `I18nConfig.kt` (CompositeMessageSource), `DatabaseMessageSource.kt` (Caffeine cache), `auth-messages.properties` + `auth-messages_vi.properties` — **ĐÃ IMPLEMENT**

### FR-004: Error response dùng ProblemDetail (RFC 9457) với i18n detail [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải trả error response dạng `ProblemDetail` với `detail` field là message đã i18n, `errorCode` (machine-readable), `Content-Type: application/problem+json`
- **Validation**: Error response chứa `type`, `title`, `status`, `detail` (localized), `errorCode`
- **Evidence**: `AuthControllerAdvice.handleAuthException()` → `ProblemDetail` — `GlobalExceptionHandler.kt:48`

### FR-005: Success response với i18n message [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải trả success response với `message` field i18n server-side cho tất cả endpoints thành công
- **Validation**: Logout → `{"message": "Đã đăng xuất thành công"}`; Login → response chứa localized message
- **Evidence**: `CqrsAuthController` đã dùng `messageSource.getMessage()` cho logout, change-password, forgot-password — `CqrsAuthController.kt:172,182,189`; cần mở rộng cho tất cả endpoints

### FR-006: Content-Language response header [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải set `Content-Language` response header trên tất cả responses để indicate ngôn ngữ thực tế của message
- **Validation**: `Content-Language: vi` khi resolve locale = `vi`
- **Evidence**: `AuthControllerAdvice.setContentLanguageHeader()` đã implement cho error responses — `GlobalExceptionHandler.kt:134`; cần mở rộng cho success responses

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
- **Evidence**: `DatabaseMessageSource.resolveCode()` trả `null` khi not found → `AbstractMessageSource` delegate to parent — `DatabaseMessageSource.kt:43`; `resolveMessage()` dùng `defaultMessage` param — `GlobalExceptionHandler.kt:128`

### FR-011: Client metadata filter cho logging (X-App-Version, X-Client-Platform → MDC) [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải extract `X-App-Version`, `X-Client-Platform` từ request headers và đưa vào MDC cho audit logging
- **Validation**: MDC chứa `appVersion`, `clientPlatform` trong mỗi request scope
- **Evidence**: Cần tạo `ClientMetadataFilter` (OncePerRequestFilter)

### FR-012: Supported locales whitelist [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải giới hạn supported locales (`en`, `vi`) và fallback unsupported locale sang `en`
- **Validation**: `Accept-Language: ja` → fallback `en`; `Accept-Language: vi` → locale `vi`
- **Evidence**: Cần configure `AcceptHeaderLocaleResolver` với supported locales whitelist

### FR-013: Idempotent locale resolution [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải đảm bảo locale resolution idempotent — cùng `Accept-Language` header luôn trả cùng ngôn ngữ
- **Validation**: N requests với cùng `Accept-Language: vi` → N responses với `Content-Language: vi`
- **Evidence**: `AcceptHeaderLocaleResolver` là stateless (Spring built-in) → inherently idempotent

## 3. Non-functional Requirements

- **NFR-001**: Message resolution overhead < 5ms (Caffeine cached), < 50ms (DB cold hit)
- **NFR-002**: Backward compatible — existing API consumers không bị break nếu không gửi `Accept-Language` (fallback `en`)
- **NFR-003**: Message bundle loading không ảnh hưởng startup time (< 100ms overhead)
- **NFR-004**: All responses phải có `Content-Language` header — 100% coverage
- **NFR-005**: Cache hit ratio > 95% cho DatabaseMessageSource (Caffeine 5-min TTL, maxSize=500)

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (error i18n) và FR-005 (success i18n) có cùng mechanism (MessageSource) nhưng scope khác nhau — giữ tách biệt.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-010**: Fallback chain — domain requirement để tránh NPE/missing translation crash. Evidence: `DatabaseMessageSource.resolveCode()` trả `null` → delegate to parent.
- **FR-011**: Client metadata filter → MDC — audit logging best practice cho multi-platform service.
- **FR-012**: Locale whitelist — bảo mật, tránh resource exhaustion qua unsupported locale.
- **FR-013**: Idempotent resolution — deterministic behavior requirement cho caching/debugging.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Spring MessageSource | i18n message resolution | Built-in framework feature, đã configured trong `I18nConfig.kt` |
| Caffeine Cache | In-process cache cho DatabaseMessageSource | 5-min TTL, maxSize=500, đã implement |
| PostgreSQL (i18n_messages) | Runtime message storage | JPA entity `I18nMessageEntity`, đã implement |
| react-i18next | Frontend UI text i18n | Đã setup sẵn, chỉ cần sync `Accept-Language` |

## 6. Assumptions

- ⚠️ Assumption: Supported locales ban đầu: `en` (default), `vi`. Format BCP 47. Mở rộng `km` sau — Lý do: research brief confirmed en + vi only.
- ⚠️ Assumption: `BaseControllerAdvice` (base-core) inject `MessageSource` optional (nullable) để backward compatible — Lý do: auth-service đã override với `AuthControllerAdvice`; other services dùng base-core không bị break.
- ⚠️ Assumption: `X-App-Version` đọc từ build config hoặc env variable — Lý do: standard practice, `package.json` version.
- ⚠️ Assumption: Caffeine cache 5-min TTL là acceptable delay cho message updates — Lý do: i18n message changes rare, production standard.
- ⚠️ Assumption: E2EE error codes (AUTH_030-039) và Anonymous error codes (AUTH_040-044) cũng cần i18n messages — Lý do: cùng `AuthException` hierarchy, handled by same `AuthControllerAdvice`.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-008: "semantic version" cần define regex cụ thể |
| Đầy đủ (Completeness) | 22/25 | FR-003: chưa specify encoding (UTF-8 implicit); missing E2EE/Anonymous i18n messages in bundles |
| Nhất quán (Consistency) | 25/25 | — |
| Kiểm thử được (Testability) | 18/25 | FR-005: "i18n message" cần example value; FR-013: "idempotent" hard to verify automated |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|----------------|
| 1 | Clarity | -2 | FR-008 | "semantic version" chưa define regex pattern | Thêm pattern `^\d+\.\d+\.\d+$` — ĐÃ BỔ SUNG |
| 2 | Completeness | -3 | FR-003 | File encoding, E2EE/Anonymous messages chưa có trong bundles | auth-messages*.properties chỉ có 21/33 error codes; cần bổ sung AUTH_030-044 |
| 3 | Testability | -4 | FR-005 | "i18n message" thiếu expected example | Thêm example: logout → "Đã đăng xuất thành công" |
| 4 | Testability | -3 | FR-013 | "idempotent" khó verify automated | Thêm: "given same header, N calls → same Content-Language" |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | `BaseControllerAdvice` là shared library — refactor MessageSource injection ảnh hưởng tất cả services dùng base-core | FR-001 | Inject `MessageSource?` nullable, fallback existing behavior. Auth-service đã có `AuthControllerAdvice` override. |
| 2 | Risk | 🟡 | Message bundles thiếu E2EE (AUTH_030-039) và Anonymous (AUTH_040-044) error codes — 12 msgCode keys missing trong auth-messages*.properties | FR-003 | Bổ sung 12 message keys cho cả en + vi bundles |

> Không phát hiện 🔴 Critical issues.

## 9. Open Questions

Không có câu hỏi mở. Các OQ từ research đã được resolved:
- ~~OQ-001: Content-Language via filter or per-controller?~~ → Per ControllerAdvice (error) + per Controller (success). Servlet filter nếu cần 100% coverage.
- ~~OQ-002: BaseControllerAdvice MessageSource injection?~~ → Auth-service's `AuthControllerAdvice` đã inject. Base-core optional.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Authentication & Authorization (auth-service)
- Admin Dashboard Frontend (admindashboard)
- Shared Library (base-core) — reference only, minimal change

### 10.2 Flow Type
Command — Cross-cutting concern (i18n message resolution), áp dụng cho tất cả endpoints. Không có OTP/confirmation flow.

### 10.3 Candidate Services
- **auth-service**: Primary — `AuthControllerAdvice` (already MessageSource-integrated), `CqrsAuthController` (partially i18n), `I18nConfig`, `DatabaseMessageSource`, message bundles. Cần extend success i18n + bổ sung missing message keys.
- **admindashboard**: Frontend — `api.ts` cần inject headers, forms cần cleanup hardcoded error messages.
- **base-core**: Reference — `ApiResponse`, `BaseControllerAdvice`, `ErrorCodeBase`. Minimal/no changes if auth-service handles all advice.

### Detection Evidence
- Keyword: `MessageSource` → Module: `shared/exception` → File: `GlobalExceptionHandler.kt` (line 33)
- Keyword: `I18nConfig` → Module: `shared/config` → File: `I18nConfig.kt`
- Keyword: `DatabaseMessageSource` → Module: `shared/i18n` → File: `DatabaseMessageSource.kt`
- Keyword: `I18nMessageEntity` → Module: `shared/i18n` → File: `I18nMessageEntity.kt`
- Keyword: `messageSource.getMessage` → Module: `auth/adapter/in/web` → File: `CqrsAuthController.kt` (lines 172, 182, 189)
- Keyword: `auth-messages` → Module: `resources/messages` → File: `auth-messages.properties`, `auth-messages_vi.properties`
- Keyword: `AuthErrorCode.msgCode` → Module: `shared/exception` → File: `AuthErrorCode.kt` (33 error codes)

### 10.4 External Integrations
- Spring `MessageSource` (built-in framework feature — already configured)
- PostgreSQL `i18n_messages` table (runtime message storage — already configured)
- Caffeine cache (in-process, already configured in DatabaseMessageSource)
- react-i18next (frontend UI text — already setup)

### 10.5 Required Modules
- `auth-service/shared/config` — I18nConfig (existing), locale resolver config
- `auth-service/shared/exception` — AuthControllerAdvice (existing, extend Content-Language for success)
- `auth-service/shared/i18n` — DatabaseMessageSource (existing), I18nMessageEntity (existing)
- `auth-service/auth/adapter/in/web` — Controllers (extend success i18n)
- `auth-service/auth/adapter/in/web/filter` — ClientMetadataFilter (NEW)
- `auth-service/resources/messages` — message bundles (extend with missing keys)
- `admindashboard/src/utils` — api.ts header injection
- `admindashboard/src/@i18n` — language sync

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Chọn ngôn ngữ (vi/en) | Frontend react-i18next |
| 2 | Frontend App | Set global headers: `Accept-Language: vi-VN`, `X-App-Version: 1.0.0`, `X-Client-Platform: web` | ky HTTP client `beforeRequest` hook |
| 3 | Frontend App | Gửi API request | ky → Auth Service |
| 4 | Auth Service | `AcceptHeaderLocaleResolver` resolve locale từ `Accept-Language` header | Spring MVC → `LocaleContextHolder` |
| 5 | Auth Service | `ClientMetadataFilter` extract `X-App-Version`, `X-Client-Platform` → MDC | OncePerRequestFilter |
| 6 | Auth Service | Controller/Handler xử lý business logic | CQRS pattern |
| 7a | Auth Service | (Success) `MessageSource.getMessage(key, args, locale)` → build response with localized message | Controller → MessageSource |
| 7b | Auth Service | (Error) Exception → `AuthControllerAdvice` → `resolveMessage()` → build `ProblemDetail` | ControllerAdvice → MessageSource |
| 8 | Auth Service | Set `Content-Language` response header | HttpServletResponse |
| 9 | Frontend App | (Success) Show `response.message` trực tiếp | React UI toast/notification |
| 10 | Frontend App | (Error) Show `problem.detail` trực tiếp, use `errorCode` cho client-side logic | React UI error display |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Research: UC-001 | TBD | `AuthControllerAdvice` (`GlobalExceptionHandler.kt`) [EXISTING] | Mapped |
| FR-002 | Research: UC-001 | TBD | Spring `AcceptHeaderLocaleResolver` [EXISTING] | Mapped |
| FR-003 | Research: UC-001 | TBD | `I18nConfig.kt`, `DatabaseMessageSource.kt`, `auth-messages*.properties` [EXISTING] | Mapped |
| FR-004 | Research: UC-001 | TBD | `AuthControllerAdvice` (`GlobalExceptionHandler.kt`) [EXISTING] | Mapped |
| FR-005 | Research: UC-003 | TBD | `CqrsAuthController.kt` [MODIFY], other controllers [MODIFY] | Mapped |
| FR-006 | Research: UC-001,003 | TBD | `AuthControllerAdvice` [EXISTING error], Controllers [MODIFY success] | Mapped |
| FR-007 | Research: UC-002 | TBD | `api.ts` [MODIFY], `I18nProvider.tsx` [MODIFY] | Mapped |
| FR-008 | Research: UC-002 | TBD | `api.ts` [MODIFY] | Mapped |
| FR-009 | Research: UC-001,003 | TBD | Frontend forms (`JwtSignInForm.tsx`, `SignInPageForm.tsx`) [MODIFY] | Mapped |
| FR-010 | Enriched | TBD | `DatabaseMessageSource.kt` [EXISTING], `AuthControllerAdvice` [EXISTING] | Mapped |
| FR-011 | Enriched | TBD | `ClientMetadataFilter.kt` [ADD] | Mapped |
| FR-012 | Enriched | TBD | Locale resolver config [ADD] | Mapped |
| FR-013 | Enriched | TBD | `AcceptHeaderLocaleResolver` [EXISTING — stateless] | Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
Feature này thuộc dạng **cross-cutting concern** — ảnh hưởng toàn bộ API layer nhưng thay đổi chủ yếu ở infrastructure level (config, advice, filter). Rủi ro rất thấp vì:

1. **90%+ infrastructure đã implement**: `I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice` (MessageSource integration), message bundles (en + vi, 44 keys)
2. `CqrsAuthController` đã dùng `messageSource.getMessage()` cho 3 endpoints (logout, change-password, forgot-password) — pattern đã proven
3. `AuthErrorCode.msgCode` convention match message bundle keys exactly (e.g., `auth.rate_limited` → `auth-messages.properties`)
4. `AcceptHeaderLocaleResolver` là built-in Spring → zero custom code cho locale resolution

**Remaining work (~3 developer-days):**
- Extend success i18n cho tất cả controller endpoints (login, register, refresh, switch-domain, etc.)
- Create `ClientMetadataFilter` cho MDC logging
- Frontend: inject `Accept-Language` + metadata headers vào ky client
- Frontend: cleanup hardcoded error messages → show server message directly
- Bổ sung 12 missing message keys (E2EE AUTH_030-039, Anonymous AUTH_040-044) vào bundles

### Related Features / Precedents
- `auth-login-admin` (archived: `openspec/changes/archive/2026-08-11-auth-login-admin/`) — triển khai `AuthControllerAdvice` + `ProblemDetail` error format + `I18nConfig`. Feature này là evolution — extend i18n coverage.
- `anonymous-login-optimization` (current: `openspec/changes/anonymous-login-optimization/`) — added `AnonymousExceptions` (AUTH_040-044) which need i18n message keys in bundles.

### Integration Notes
- **Spring MessageSource**: Built-in, zero external dependency. `I18nConfig.kt` provides CompositeMessageSource chain.
- **DatabaseMessageSource**: Caffeine cache (5-min TTL, maxSize=500). DB unavailable → file bundle fallback. Transparent to consumers.
- **react-i18next**: Frontend i18n đã setup, có `en` locale JSON. Cần sync `i18n.language` → `Accept-Language` header.
- **Backward compatible**: Client không gửi `Accept-Language` → server dùng `en` (default). Zero breaking changes.

### Suggested Approach
1. **auth-service backend FIRST**: 
   - Bổ sung missing message keys (E2EE, Anonymous) vào `auth-messages*.properties`
   - Create `ClientMetadataFilter` → MDC
   - Configure `AcceptHeaderLocaleResolver` với locale whitelist
   - Extend success i18n cho remaining controllers
2. **frontend SECOND**:
   - `api.ts` — inject `Accept-Language`, `X-App-Version`, `X-Client-Platform` vào global headers
   - `I18nProvider.tsx` — sync language change → `Accept-Language` header
   - Cleanup hardcoded error messages trong forms
3. **base-core (optional)**: `BaseControllerAdvice` inject `MessageSource?` nullable — chỉ nếu cần share pattern across services

### Context from Confluence Images
N/A
