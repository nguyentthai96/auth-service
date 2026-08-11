# SRS: API Response I18n Standard

## 1. Feature Overview

Chuẩn hóa API request/response format với i18n support: Server render message hoàn chỉnh (interpolated) theo `Accept-Language` header → client chỉ hiển thị. Dual response: `ApiResponse<T>` (2xx), `ProblemDetail` (4xx/5xx). Dual message source: file (static) + database (dynamic).

## 2. Functional Requirements

### FR-001: Server render message đa ngôn ngữ [IDEA]
- Server phải resolve message hoàn chỉnh (đã interpolate params) bằng ngôn ngữ client request
- Message key = `ErrorCodeBase.msgCode` (e.g., `auth.rate_limited`)
- Args interpolation: `MessageSource.getMessage(msgCode, args, locale)`
- Ví dụ: key=`auth.session_limit`, args=[3] → EN: "Maximum sessions (3) reached" / VI: "Đã đạt tối đa 3 phiên"
- **Error**: N/A — internal infrastructure

### FR-002: Locale resolution từ Accept-Language [IDEA]
- Spring `AcceptHeaderLocaleResolver` resolve locale từ `Accept-Language` header
- Supported locales: `en` (default), `vi`
- Missing/unsupported header → fallback `en`
- `vi-VN` → match `vi` → OK
- `km` → no match → fallback `en`
- Format: IETF BCP 47 (RFC 5646)
- **Error**: Không phát sinh error — fallback mechanism

### FR-003: Message bundle infrastructure [IDEA]
- **Static bundles** (file): `messages.properties` (en), `messages_vi.properties` (vi)
- base-core bundles: common API error codes (`api.bad_request`, `api.not_found`, `api.system_error`, `api.forbidden`, `api.validation_error`, `api.success`)
- auth-service bundles: 21 `AuthErrorCode` message keys (`auth.invalid_credentials`, `auth.account_locked`, ...)
- Encoding: UTF-8
- Location: `classpath:messages/` (base-core), `classpath:messages/` (auth-service)
- Spring `ReloadableResourceBundleMessageSource` — fallback chain built-in
- **Error**: Missing key → fallback to default locale → fallback to `ErrorCodeBase.description`

### FR-003B: Database message source [IDEA — USER DECISION]
- **Dynamic bundles** (database): `i18n_messages` table
- Schema: `code` (message key), `locale` (BCP 47), `message` (resolved text), `module` (service grouping), `is_active`, `created_at`, `updated_at`
- `DatabaseMessageSource extends AbstractMessageSource` — query by (code, locale)
- Cache: Caffeine (5 min TTL) — avoid DB hit per request
- Fallback: DB miss → delegate to file-based `MessageSource`
- CRUD management: future admin API (out of scope for this change)
- **Error**: `I18N_DB_UNAVAILABLE` — fallback to file bundle silently, log warning

### FR-004: Error response ProblemDetail i18n [IDEA]
- `AuthControllerAdvice.handleAuthException()` → `ProblemDetail.setDetail(resolvedMessage)`
- `resolvedMessage = messageSource.getMessage(authError.msgCode, args, locale)`
- Response `Content-Type: application/problem+json`
- `Content-Language` header = resolved locale
- **Error**: `AUTH_001`→`AUTH_021` (21 codes) — all resolved via MessageSource

### FR-005: Success response ApiResponse i18n [IDEA]
- `ApiResponse.success(data, message)` → message = `messageSource.getMessage("api.success", null, locale)` hoặc custom key
- Controllers có thể pass custom success message key
- **Error**: N/A

### FR-006: Content-Language response header [IDEA]
- Server set `Content-Language` response header = locale thực tế đã dùng
- Implement via `LocaleContextHolder.getLocale()` in advice/filter
- **Error**: N/A

### FR-007: Client Accept-Language header [IDEA]
- Frontend inject `Accept-Language` header vào mọi API request
- Sync với `i18n.language` khi change language
- Format: BCP 47 (e.g., `vi`, `en`)
- Hook vào `api.ts` `setGlobalHeaders()`
- **Error**: Missing header → server fallback `en`

### FR-008: Client X-App-Version header [IDEA]
- Frontend inject `X-App-Version: {version}` header
- Version từ `import.meta.env.VITE_APP_VERSION` hoặc `package.json` version
- Format: semver `x.y.z`
- **Error**: Missing header → server ignores (audit only)

### FR-009: Client hiển thị message trực tiếp [IDEA]
- Error: `setErrorMessage(problem.detail)` — no switch/case on errorCode
- Success: show `response.message`
- Frontend KHÔNG tự build error message string
- Remove 12 hardcoded strings trong `JwtSignInForm.tsx`
- **Error**: `problem.detail` undefined → fallback "An error occurred"

### FR-010: Fallback chain khi thiếu translation [ENRICHED]
- Chain: DB `i18n_messages` → file `messages_{locale}` → file `messages` (default) → `ErrorCodeBase.description`
- Missing key KHÔNG gây exception — trả fallback message
- **Error**: Log warning `"Missing i18n key: {key}, locale: {locale}"`

### FR-011: Client metadata X-Client-Platform [ENRICHED]
- Header `X-Client-Platform: web` cho admin dashboard
- Server dùng cho audit/logging, không business logic
- **Error**: N/A

### FR-012: Supported locales whitelist [ENRICHED]
- Whitelist config: `app.i18n.supported-locales=en,vi`
- Unsupported locale → fallback `en`
- Config via `application.yml`
- **Error**: N/A

### FR-013: Idempotent locale resolution [ENRICHED]
- Cùng `Accept-Language` header → cùng resolved locale → cùng `Content-Language` response
- Spring `AcceptHeaderLocaleResolver` đảm bảo deterministic
- **Error**: N/A

## 3. Non-functional Requirements

- NFR-001: MessageSource loading < 100ms startup overhead
- NFR-002: `getMessage()` latency < 1ms (in-memory cache)
- NFR-003: Backward compatible — existing API consumers không break nếu không gửi `Accept-Language`
- NFR-004: DB MessageSource cache TTL = 5 min (Caffeine)
- NFR-005: DB unavailable → degrade gracefully to file bundles (no error to client)

## 4. Database Schema

### Table: `i18n_messages`

```sql
CREATE TABLE i18n_messages (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(128) NOT NULL,         -- message key (e.g., 'auth.rate_limited')
    locale        VARCHAR(10)  NOT NULL,         -- BCP 47 locale (e.g., 'en', 'vi')
    message       TEXT         NOT NULL,         -- resolved message template with {0}, {1} placeholders
    module        VARCHAR(64)  NOT NULL DEFAULT 'common', -- service grouping
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    BIGINT       NOT NULL,         -- epoch millis
    updated_at    BIGINT       NOT NULL,         -- epoch millis
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
| `Content-Language` | Response | Always | `vi`, `en` |

### Response Format

#### Success (2xx)
```json
{
  "code": "00",
  "msgCode": "SUCCESS",
  "message": "Đăng nhập thành công",  // ← i18n resolved
  "data": { ... },
  "timestamp": 1723372800000
}
```

#### Error (4xx/5xx) — ProblemDetail RFC 9457
```json
{
  "type": "https://auth-service/errors/rate_limited",
  "title": "AUTH_020",
  "status": 429,
  "detail": "Quá nhiều lần đăng nhập từ IP",  // ← i18n resolved
  "errorCode": "AUTH_020",
  "retryAfterSeconds": 60,
  "dimension": "IP"
}
```
