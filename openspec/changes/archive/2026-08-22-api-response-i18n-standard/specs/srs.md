# SRS: API Response I18n Standard

> [CHANGED] Updated 2026-08-22 — reflects current codebase state where ~97% infrastructure is implemented. FRs with "ĐÃ IMPLEMENT" status are verified existing; remaining FRs cover LoginRateLimitFilter i18n + frontend changes.

## 1. Feature Overview

Chuẩn hóa API request/response format với i18n support: Server render message hoàn chỉnh (interpolated) theo `Accept-Language` header → client chỉ hiển thị. Dual response: `ApiResponse<T>` (2xx), `ProblemDetail` (4xx/5xx). Dual message source: file (static) + database (dynamic). Content-Language header trên mọi response via Servlet Filter (100% coverage).

**Current implementation status**: ~97% complete. Infrastructure (`I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice`, `ContentLanguageFilter`, `ClientMetadataFilter`, message bundles 55 keys en+vi, controller i18n for 10 action endpoints) is all EXISTING. Remaining: `LoginRateLimitFilter` i18n fix + frontend header injection + form cleanup.

## 2. Functional Requirements

### FR-001: Server render message đa ngôn ngữ cho error response [URD] — ĐÃ IMPLEMENT
- Server phải resolve message hoàn chỉnh (đã interpolate params) bằng ngôn ngữ client request
- Message key = `AuthErrorCode.msgCode` (e.g., `auth.rate_limited`)
- Args interpolation: `MessageSource.getMessage(msgCode, args, locale)`
- Ví dụ: key=`auth.session_limit`, args=[3] → EN: "Maximum active sessions (3) reached" / VI: "Đã đạt tối đa 3 phiên hoạt động"
- **Evidence**: `AuthControllerAdvice.resolveMessage()` — `GlobalExceptionHandler.kt:125-131`; `extractMessageArgs()` maps exception properties — `GlobalExceptionHandler.kt:107-117`
- **Error**: N/A — internal infrastructure

### FR-002: Locale resolution từ Accept-Language [URD] — ĐÃ IMPLEMENT
- Spring `AcceptHeaderLocaleResolver` resolve locale từ `Accept-Language` header
- Supported locales: `en` (default), `vi`
- Missing/unsupported header → fallback `en`
- Format: IETF BCP 47 (RFC 5646)
- **Evidence**: `I18nConfig.kt:39-44` — `AcceptHeaderLocaleResolver` with supportedLocales = [en, vi], defaultLocale = ENGLISH
- **Error**: Không phát sinh error — fallback mechanism

### FR-003: Message bundle infrastructure (DB + file) [URD] — ĐÃ IMPLEMENT
- **Static bundles**: `auth-messages.properties` (en, 70 lines, 55 keys), `auth-messages_vi.properties` (vi, 70 lines, 55 keys)
- **Dynamic bundles**: `DatabaseMessageSource` extends `AbstractMessageSource`, Caffeine cache (5-min TTL, maxSize=500)
- **Composite chain**: `I18nConfig.kt:49-63` → DatabaseMessageSource → ReloadableResourceBundleMessageSource
- **Evidence**: `I18nConfig.kt`, `DatabaseMessageSource.kt`, `I18nMessageEntity.kt`, `I18nMessageRepository.kt` — all EXISTING
- **Error**: Missing key → fallback chain: DB → file locale → file default → `ErrorCodeBase.description`

### FR-004: Error response dùng ProblemDetail (RFC 9457) với i18n detail [URD] — ĐÃ IMPLEMENT
- `AuthControllerAdvice.handleAuthException()` → `ProblemDetail.setDetail(resolvedMessage)`
- Response `Content-Type: application/problem+json`
- Covers ALL AuthException subtypes including E2EE (AUTH_030-039) and Anonymous (AUTH_040-044)
- **Evidence**: `GlobalExceptionHandler.kt:48-99` — sets type, title, status, detail (i18n), errorCode, extra properties
- **Error**: `AUTH_001`→`AUTH_044` (44 codes) — all resolved via MessageSource

### FR-004B: LoginRateLimitFilter ProblemDetail i18n [NEW — from brainstorm Issue #1]
- `LoginRateLimitFilter.writeRateLimitResponse()` MUST resolve `detail` field via MessageSource instead of using `ex.message` (hardcoded English)
- Filter MUST parse `Accept-Language` header directly (cannot rely on `LocaleContextHolder` — filter runs pre-DispatcherServlet)
- Filter MUST set `Content-Language` response header
- Supported locales whitelist `[en, vi]` must match `I18nConfig` configuration
- **Evidence**: `LoginRateLimitFilter.kt:94-107` — currently uses `ProblemDetail.forStatusAndDetail(429, ex.message)` (hardcoded)
- **Error**: `AUTH_020` — rate limit error with i18n detail

### FR-005: Success response với i18n message [URD] — ĐÃ IMPLEMENT
- Action endpoints include i18n `message` field via `messageSource.getMessage(key, null, locale)`
- Data-only endpoints (login, refresh, introspect, get sessions) → no forced i18n message
- **Evidence**: `CqrsAuthController.kt` (5 endpoints: register, logout, change-password, forgot-password, switch-domain), `SessionController.kt` (2: session_revoked, all_sessions_revoked), `AccountLifecycleController.kt` (3: account_deactivated, deletion_requested, deletion_cancelled) — ALL use `messageSource.getMessage()`
- **Error**: N/A

### FR-006: Content-Language response header [URD] — ĐÃ IMPLEMENT (100% coverage)
- `ContentLanguageFilter` (OncePerRequestFilter, @Order LOWEST_PRECEDENCE - 10) sets `Content-Language` on ALL `/api/**` responses
- Defense-in-depth: `AuthControllerAdvice.setContentLanguageHeader()` also sets for error responses
- **Evidence**: `ContentLanguageFilter.kt` (44 lines), `GlobalExceptionHandler.kt:134-136`
- **Error**: N/A

### FR-007: Client gửi Accept-Language header [URD] — CHƯA IMPLEMENT
- Frontend inject `Accept-Language` header vào mọi API request
- Sync với `i18n.language` khi change language
- Format: BCP 47 (e.g., `vi`, `en`)
- Hook vào `api.ts` `setGlobalHeaders()`
- **Error**: Missing header → server fallback `en`

### FR-008: Client gửi metadata headers (X-App-Version, X-Client-Platform) [URD] — CHƯA IMPLEMENT
- Frontend inject `X-App-Version: {version}` (semver `^\d+\.\d+\.\d+$`) và `X-Client-Platform: web`
- Version từ `import.meta.env.VITE_APP_VERSION` hoặc `package.json` version
- **Error**: Missing header → server ignores (audit only), MDC value = "unknown"

### FR-009: Client hiển thị message trực tiếp [URD] — CHƯA IMPLEMENT
- Error: `setErrorMessage(problem.detail)` — no switch/case on errorCode
- Success: show `response.message`
- Frontend KHÔNG tự build error message string
- Remove hardcoded strings trong forms
- **Error**: `problem.detail` undefined → fallback "An error occurred"

### FR-010: Fallback chain khi thiếu translation [ENRICHED] — ĐÃ IMPLEMENT
- Chain: DB `i18n_messages` → file `messages_{locale}` → file `messages` (default) → `ErrorCodeBase.description`
- Missing key KHÔNG gây exception — trả fallback message
- **Evidence**: `DatabaseMessageSource.resolveCode()` returns `null` → delegate to parent. `resolveMessage()` uses `defaultMessage` param.
- **Error**: Log warning `"Missing i18n key: {key}, locale: {locale}"`

### FR-011: Client metadata filter cho logging [ENRICHED] — ĐÃ IMPLEMENT
- `ClientMetadataFilter` (OncePerRequestFilter, @Order HIGHEST_PRECEDENCE + 10) extracts headers → MDC
- MDC keys: `appVersion`, `clientPlatform`; cleanup in finally block
- **Evidence**: `ClientMetadataFilter.kt` (57 lines)
- **Error**: Missing header → MDC value = "unknown"

### FR-012: Supported locales whitelist [ENRICHED] — ĐÃ IMPLEMENT
- `AcceptHeaderLocaleResolver` configured with `supportedLocales = [en, vi]`, `defaultLocale = ENGLISH`
- **Evidence**: `I18nConfig.kt:42-43`
- **Error**: N/A

### FR-013: Idempotent locale resolution [ENRICHED] — ĐÃ IMPLEMENT
- `AcceptHeaderLocaleResolver` stateless → inherently idempotent
- Same `Accept-Language` → same resolved locale → same `Content-Language`
- **Evidence**: Spring built-in, `ContentLanguageFilter` reads from `LocaleContextHolder`
- **Error**: N/A

## 3. Non-functional Requirements

- NFR-001: MessageSource loading < 100ms startup overhead
- NFR-002: `getMessage()` latency < 1ms (in-memory Caffeine cache hit), < 50ms (DB cold hit)
- NFR-003: Backward compatible — existing API consumers không break nếu không gửi `Accept-Language`
- NFR-004: ALL responses MUST have `Content-Language` header — 100% coverage via ContentLanguageFilter (+ LoginRateLimitFilter for rate limit bypass path)
- NFR-005: DB MessageSource cache TTL = 5 min (Caffeine, maxSize=500), hit ratio > 95%
- NFR-006: DB unavailable → degrade gracefully to file bundles (no error to client, log warning)

## 4. Database Schema

### Table: `i18n_messages` — ĐÃ IMPLEMENT

```sql
CREATE TABLE i18n_messages (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(128) NOT NULL,
    locale        VARCHAR(10)  NOT NULL,
    message       TEXT         NOT NULL,
    module        VARCHAR(64)  NOT NULL DEFAULT 'common',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NOT NULL,
    CONSTRAINT uq_i18n_code_locale UNIQUE (code, locale)
);

CREATE INDEX idx_i18n_messages_code ON i18n_messages (code);
CREATE INDEX idx_i18n_messages_module ON i18n_messages (module);
```

## 5. API Contract Changes

### Headers (all endpoints)

| Header | Direction | Required | Example |
|--------|-----------|----------|---------|
| `Accept-Language` | Request | Optional (fallback en) | `vi`, `en` |
| `X-App-Version` | Request | Optional | `1.0.0` |
| `X-Client-Platform` | Request | Optional | `web` |
| `Content-Language` | Response | Always (NFR-004) | `vi`, `en` |

### Response Format

#### Success (2xx)
```json
{
  "code": "00",
  "msgCode": "SUCCESS",
  "message": "Đăng ký thành công",
  "data": { "..." },
  "timestamp": 1723372800000
}
```

#### Error (4xx/5xx) — ProblemDetail RFC 9457
```json
{
  "type": "https://auth-service/errors/rate_limited",
  "title": "AUTH_020",
  "status": 429,
  "detail": "Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây.",
  "errorCode": "AUTH_020",
  "retryAfterSeconds": 60,
  "dimension": "IP"
}
```

### Message Interpolation Examples

| Error Code | Args | EN Message | VI Message |
|-----------|------|-----------|------------|
| AUTH_020 | [60, "IP"] | Too many login attempts (IP). Please wait 60 seconds | Quá nhiều lần đăng nhập (IP). Vui lòng đợi 60 giây |
| AUTH_021 | [3] | Maximum active sessions (3) reached | Đã đạt tối đa 3 phiên hoạt động |
| AUTH_041 | [1024, 2048] | Data size (1024 bytes) exceeds the maximum allowed (2048 bytes) | Kích thước dữ liệu (1024 bytes) vượt quá giới hạn cho phép (2048 bytes) |
