---
type: brainstorm_notes
change: api-response-i18n-standard
date: 2026-08-11
selected_direction: "Hybrid Cascade Locale Detection + Spring MessageSource i18n"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: API Response I18n Standard

## Date
2026-08-11

## Context
Feature chuẩn hóa API request/response giữa client-server: Server render message i18n hoàn chỉnh (đã interpolate params), client chỉ hiển thị. Dual format: `ApiResponse<T>` (success), `ProblemDetail` (error). User yêu cầu chuẩn quốc tế, `en` default + `vi`, auto-detect location qua browser.

## Questions Asked & Answers

- Q1: Có cần thêm locale `km` (Khmer) ngay từ phase đầu?
  → A: Không. Hiện tại chỉ `en` (default) + `vi`. Dùng locale chuẩn quốc tế (BCP 47). Mở rộng sau.

- Q2: Locale detection mechanism?
  → A: User muốn "detection location để lấy ngôn ngữ nếu user không set". Brainstorm kết luận: dùng `navigator.languages` (browser tự detect theo OS locale), KHÔNG dùng IP geolocation.

## Approaches Considered

### Approach 1: Browser-Only Detection (navigator.languages)
- **Mô tả**: Client đọc `navigator.languages` → match supported locales → set `Accept-Language`
- **Pros**: Zero dependency, privacy-friendly, nhanh
- **Cons**: Browser setting có thể khác user intent (shared computer)
- **Verdict**: Tốt nhưng chưa persist user choice

### Approach 2: IP Geolocation Lookup
- **Mô tả**: Server hoặc client gọi IP geolocation API → xác định country → map country → language
- **Pros**: Real location awareness
- **Cons**: External dependency (API cost, rate limit), privacy concern (GDPR), inaccurate (VPN/proxy), language ≠ location (expat scenario)
- **Verdict**: ❌ Rejected — quá phức tạp, privacy risk, không chính xác

### Approach 3: Hybrid Cascade (SELECTED ✅)
- **Mô tả**: Priority cascade: (1) localStorage → (2) navigator.languages → (3) default "en"
- **Pros**: Respects browser location proxy, zero external dependency, privacy-friendly, persists user choice, deterministic
- **Cons**: Browser setting chưa chính xác → user chỉ cần switch 1 lần → remembered
- **Verdict**: ✅ Best balance — "location detection" qua browser setting, persist qua localStorage

## Selected Direction

**Hybrid Cascade Locale Detection** — dùng `navigator.languages` (đã reflect OS locale ≈ location) làm auto-detect, persist vào localStorage, ưu tiên user explicit choice.

### Locale Resolution Flow

```
┌──────────────────────────────────────────────────┐
│ 1. localStorage("user_language")                 │ ← Highest priority
│    ↓ not found                                   │
│ 2. navigator.languages → match supported locales │ ← Browser/OS detection
│    ↓ no match                                    │
│ 3. "en" (default)                                │ ← Fallback
└──────────────────────────────────────────────────┘
```

### Locale Standard: IETF BCP 47 (RFC 5646)

| Element | Value |
|---------|-------|
| Supported locales | `en`, `vi` |
| Default locale | `en` |
| Header format | `Accept-Language: vi-VN, en;q=0.9` |
| Server resolution | `vi-VN` → match `vi` → OK |
| Unsupported | `km` → no match → fallback `en` |
| Message bundles | `messages.properties` (en), `messages_vi.properties` (vi) |

### Server Architecture

```
Request → AcceptHeaderLocaleResolver → LocaleContextHolder
                                            ↓
                                      MessageSource.getMessage(
                                          code = "auth.rate_limited",
                                          args = [5, "IP"],
                                          locale = resolved
                                      )
                                            ↓
                                      ProblemDetail.detail = "Too many login attempts from IP"
                                      (or Vietnamese: "Quá nhiều lần đăng nhập từ IP")
```

### Frontend Architecture

```
First visit:
  navigator.languages → ["vi", "en"] → detect("vi")
  → i18n.changeLanguage("vi")
  → localStorage.set("user_language", "vi")
  → api.setGlobalHeaders({ "Accept-Language": "vi" })

Return visit:
  localStorage.get("user_language") → "vi"
  → i18n.changeLanguage("vi")
  → api.setGlobalHeaders({ "Accept-Language": "vi" })

Language switcher:
  user clicks "English"
  → i18n.changeLanguage("en")
  → localStorage.set("user_language", "en")
  → api.setGlobalHeaders({ "Accept-Language": "en" })
```

### Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Dùng `navigator.languages` thay vì IP geolocation | Privacy, zero cost, no external dependency |
| D2 | Persist language vào `localStorage` | Survive page refresh, no server roundtrip |
| D3 | Supported locales: `["en", "vi"]` only | User requirement, mở rộng sau |
| D4 | `en` default locale | International standard, safe fallback |
| D5 | BCP 47 locale format | IETF standard, Spring native support |
| D6 | `MessageSource` inject optional vào `BaseControllerAdvice` | Backward compatible, existing services không bị break |
| D7 | `ErrorCodeBase.msgCode` = message bundle key | Convention đã có sẵn, zero mapping effort |
| D8 | Frontend languages cần update: remove `tr`/`ar`, add `vi` | Hiện tại có `en`, `tr`, `ar` — không dùng Turkish/Arabic |

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Command (cross-cutting concern)
- Affected modules:
  - **base-core**: `BaseControllerAdvice`, `BaseCoreServletAutoConfiguration` (locale config, MessageSource)
  - **auth-service**: `AuthControllerAdvice`, message bundles, `AuthErrorCode` (21 codes → 21 message keys)
  - **admindashboard**: `I18nProvider`, `i18n.ts`, `api.ts`, `JwtSignInForm.tsx`

## Codebase Findings

### Existing Infrastructure (Reuse)
- `ApiResponse.errorLang(err, lang, msgResolver)` — already accepts resolver function → wire `MessageSource`
- `ErrorCodeBase.msgCode` convention (e.g., `auth.rate_limited`) → direct message bundle key
- `ErrorCodeBase.withArgs(*args)` — format-based interpolation fallback
- `api.ts`: `setGlobalHeaders()` / `removeGlobalHeaders()` — inject headers globally
- `I18nProvider.tsx`: `changeLanguage()` already exists → extend to sync `Accept-Language`
- `I18nContext.tsx`: `LanguageType` type already defined

### Current Issues Found
1. `I18nProvider.tsx` has wrong languages: `en`, `tr` (Turkish), `ar` (Arabic) — need `en`, `vi`
2. `i18n.ts`: minimal setup, no `languageDetector`, no `vi` translations
3. `JwtSignInForm.tsx`: ~12 hardcoded English error messages → need to use `problem.detail` from server
4. `BaseControllerAdvice` has no `MessageSource` — all messages are hardcoded English strings
5. `AuthControllerAdvice` builds `ProblemDetail.detail` with hardcoded English (e.g., `"Too many login attempts"`)

### Message Key Convention Validation

21 `AuthErrorCode` entries → 21 message keys:
```properties
# messages.properties (en)
auth.invalid_credentials=Invalid username or password
auth.account_locked=Account is locked due to failed login attempts
auth.rate_limited=Too many login attempts
auth.session_limit=Maximum active sessions exceeded
# ... (21 total)

# messages_vi.properties
auth.invalid_credentials=Sai tên đăng nhập hoặc mật khẩu
auth.account_locked=Tài khoản bị khóa do đăng nhập sai quá nhiều lần
auth.rate_limited=Quá nhiều lần đăng nhập
auth.session_limit=Đã đạt tối đa phiên hoạt động
# ... (21 total)
```

## Open Questions for Design Phase
- [RESOLVED] Locale detection method → Hybrid Cascade (navigator.languages + localStorage)
- [RESOLVED] Supported locales → `en` (default), `vi`
- [RESOLVED] Locale format → BCP 47
- [RESOLVED] Message key cho `BaseControllerAdvice` errors → Yes, define ở base-core level (`api.bad_request`, `api.not_found`, etc.), check đa ngôn ngữ theo client header
- [RESOLVED] `ApiResponse.success()` i18n → Yes, cần sửa load đa ngôn ngữ. Common codes → file `.properties`, dynamic codes → DB table `i18n_messages`

## Open Questions for URD Analysis
- Không có — đã đủ thông tin từ research + user answers
