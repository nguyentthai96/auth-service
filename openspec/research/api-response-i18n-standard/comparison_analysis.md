# Comparison Analysis: API Response & i18n Standard

## Approach Options

### Option A: Unified Single Format (ApiResponse for everything)
Gộp error vào `ApiResponse` — bỏ `ProblemDetail`.

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Standards compliance | 2 | Không follow RFC 9457, custom format |
| Client simplicity | 5 | 1 format duy nhất, parse đơn giản |
| Tooling support | 2 | Swagger/OpenAPI không auto-generate |
| Extensibility | 3 | Tự define structure |
| Migration effort | 4 | Chỉ cần refactor `AuthControllerAdvice` |
| **Total** | **16/25** | |

### Option B: Dual Format (ApiResponse + ProblemDetail) — **RECOMMENDED**
Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457). Server render message i18n.

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Standards compliance | 5 | RFC 9457 cho error, custom wrapper cho success |
| Client simplicity | 4 | 2 formats nhưng distinguish qua HTTP status |
| Tooling support | 5 | Spring native, Swagger auto-gen |
| Extensibility | 5 | `setProperty()` + `Content-Language` |
| Migration effort | 3 | Cần thống nhất base-core + auth-service |
| **Total** | **22/25** | |

### Option C: ProblemDetail for Everything
Cả success lẫn error đều dùng `ProblemDetail`.

| Criteria | Score (1-5) | Notes |
|----------|:-----------:|-------|
| Standards compliance | 3 | RFC 9457 designed for errors, not success |
| Client simplicity | 4 | 1 format |
| Tooling support | 3 | `application/problem+json` cho success → weird |
| Extensibility | 4 | |
| Migration effort | 2 | Massive rewrite |
| **Total** | **16/25** | |

## Recommendation: Option B — Dual Format

### Rationale
1. **Đã có foundation**: `ApiResponse.errorLang()` và `ErrorCodeBase.withArgs()` sẵn trong base-core
2. **RFC 9457 compliance**: Industry standard, Spring Boot 3.x native support
3. **Tách biệt rõ ràng**: HTTP status 2xx → `ApiResponse`, 4xx/5xx → `ProblemDetail`
4. **Client đơn giản**: Check HTTP status → parse tương ứng (interceptor pattern)

### Design Decisions

#### D1: Server render message hoàn chỉnh
```
# BEFORE (client-side)
Client nhận: {errorCode: "AUTH_021", maxSessions: 3}
Client build: `Maximum sessions (3) reached...`

# AFTER (server-side)
Client nhận: {errorCode: "AUTH_021", detail: "Đã đạt tối đa 3 phiên đăng nhập..."}
Client show:  problem.detail  ← done!
```

#### D2: Dual i18n strategy
| Layer | Scope | Tool | Source |
|-------|-------|------|--------|
| Server | API error/success messages | Spring `MessageSource` | `messages_vi.properties`, `messages_en.properties` |
| Frontend | UI text (buttons, labels, placeholders) | react-i18next | `{namespace}/vi.json`, `{namespace}/en.json` |

#### D3: Client metadata headers
```
Accept-Language: vi-VN, vi;q=0.9, en;q=0.8    ← standard
X-App-Version: 1.0.0                            ← custom
X-Client-Platform: web                           ← custom
```

#### D4: Thống nhất auth error format
```
# BEFORE: AuthControllerAdvice trả ProblemDetail riêng
# AFTER:  AuthControllerAdvice vẫn trả ProblemDetail nhưng:
#   - detail = server-rendered i18n message (đã interpolate)
#   - errorCode = machine-readable code (client vẫn dùng cho logic)
#   - extensions (retryAfterSeconds, maxSessions...) vẫn giữ cho client logic
```

## Gap Analysis

| Current State | Target State | Gap | Effort |
|--------------|-------------|-----|--------|
| No message bundles | `messages_{lang}.properties` per service | New files | Low |
| No locale resolution | `AcceptHeaderLocaleResolver` + filter | Config | Low |
| `ErrorCodeBase.withArgs()` exists but unused for i18n | `MessageSource.getMessage(msgCode, args, locale)` | Refactor | Medium |
| `ApiResponse.errorLang()` exists but unused | Wire `MessageSource` into `BaseControllerAdvice` | Medium |
| `AuthControllerAdvice` hardcodes English `detail` | Resolve via `MessageSource` | Medium |
| Frontend hardcodes error messages | Show `problem.detail` directly | Simplify | Low |
| No `Accept-Language` header sent | `api.ts` global header hook | Low |
| No `X-App-Version` sent | `api.ts` global header hook | Low |
