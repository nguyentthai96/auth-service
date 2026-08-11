# Web Research: API Response & i18n Standard

## Iteration 1: Server-side i18n pattern for REST APIs

### Sources
| # | Source | Key Findings |
|---|--------|-------------|
| 1 | Spring.io (RFC 9457 support) | Spring Boot 3.x native `ProblemDetail` + `MessageSource` auto-resolution via `ResponseEntityExceptionHandler` |
| 2 | Baeldung (Problem Details) | `ProblemDetail.setProperty()` for custom fields, `Content-Language` response header |
| 3 | StackOverflow (MessageSource args) | `MessageFormat` syntax `{0}`, `{1}` cho interpolation trong `.properties` |

### Key Insight
Spring Boot 3.x đã built-in hỗ trợ:
- `LocaleContextHolder.getLocale()` tự đọc `Accept-Language`
- `MessageSource.getMessage(code, args, locale)` để resolve message
- `ProblemDetail` supports i18n qua convention `problemDetail.detail.<ExceptionFQN>`

## Iteration 2: Unified response format patterns

### Sources
| # | Source | Key Findings |
|---|--------|-------------|
| 4 | Reddit / masteringbackend.com | "Dual approach": `ApiResponse<T>` cho success, `ProblemDetail` cho error |
| 5 | itnext.io | Envelope pattern: `{success, data/error, metadata}` |
| 6 | RFC 9457 spec | Standard fields: `type`, `title`, `status`, `detail`, `instance` + extensions |

### Key Insight
Industry best practice: **KHÔNG** wrap `ProblemDetail` trong `ApiResponse` — giữ 2 format riêng:
- Success → `ApiResponse<T>` (`Content-Type: application/json`)
- Error → `ProblemDetail` (`Content-Type: application/problem+json`)

Client distinguish qua:
1. HTTP status code (2xx vs 4xx/5xx)
2. `Content-Type` header
3. Presence of `type` field (ProblemDetail) vs `code` field (ApiResponse)

## Iteration 3: Client-server metadata exchange

### Sources
| # | Source | Key Findings |
|---|--------|-------------|
| 7 | Web search (API versioning) | `X-App-Version`, `X-Client-Platform` custom headers for feature flagging |
| 8 | HTTP/1.1 spec (RFC 7231) | `Accept-Language` standard → `Content-Language` response |
| 9 | Mobile API patterns | Client sends device metadata → server uses for audit, force update, A/B testing |

### Key Insight
Standard headers:
- `Accept-Language: vi-VN, vi;q=0.9, en;q=0.8` → server responds in best match
- `Content-Language: vi` → server tells client which language was used
- Custom: `X-App-Version: 1.2.3`, `X-Client-Platform: web/ios/android`

## Summary of Findings

| Aspect | Industry Standard | Current System | Gap |
|--------|------------------|----------------|-----|
| Error message rendering | Server-side (MessageSource) | Client-side (template string) | **Critical** |
| Message interpolation | `MessageFormat {0}` | `description.format(*args)` (exists but unused for i18n) | **Moderate** |
| Locale resolution | `Accept-Language` header | Not implemented | **Critical** |
| Response format | Dual (ApiResponse + ProblemDetail) | Inconsistent (3 formats) | **Moderate** |
| Client version | Custom headers | Not implemented | **Low** |
| Server `Content-Language` | Standard practice | Not implemented | **Low** |
