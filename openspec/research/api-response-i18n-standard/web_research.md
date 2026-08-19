# Kết quả nghiên cứu Internet: API Response & i18n Standard

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | api-response-i18n-standard |
| **Ngày nghiên cứu** | 2026-08-11 |
| **Số iterations** | 3 |
| **Tổng sources** | 9 unique |
| **Keywords ban đầu** | RFC 9457, ProblemDetail, MessageSource, i18n REST API, Accept-Language |
| **Keywords phát triển** | server-side message rendering, error message interpolation, Content-Language, dual response format, client metadata headers |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"RFC 9457 ProblemDetail i18n server-side message rendering"` | Spring Boot 3.x native `ProblemDetail` with `MessageSource` auto-resolution via `ResponseEntityExceptionHandler`. Convention `problemDetail.detail.<ExceptionFQN>` for auto-lookup. | `ResponseEntityExceptionHandler`, `problemDetail.detail.*` convention |
| 2 | `"Spring Boot MessageSource Accept-Language locale resolution"` | `AcceptHeaderLocaleResolver` reads `Accept-Language` → `LocaleContextHolder.setLocale()` → all downstream code accesses via `LocaleContextHolder.getLocale()`. `MessageSource.getMessage(code, args, locale)` resolves final string. | `AcceptHeaderLocaleResolver`, `LocaleContextHolder`, `MessageFormat` |
| 3 | `"API response envelope standard unified format REST"` | Industry pattern: dual format — Success → custom envelope, Error → RFC 9457 ProblemDetail. Content-Type distinguishes: `application/json` vs `application/problem+json`. | `application/problem+json`, dual format pattern |

**Takeaways Iteration 1:**
- Spring Boot 3.x has **native** ProblemDetail i18n support — no external library needed
- `Accept-Language` → `LocaleContextHolder` is the standard resolution chain
- Dual response format (success envelope + ProblemDetail) is industry best practice
- Need to explore: database-backed MessageSource, message interpolation patterns

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html | Spring MVC Error Responses | Spring Boot 3.x auto ProblemDetail via `spring.mvc.problemdetails.enabled=true`. `ResponseEntityExceptionHandler` resolves `detail` via MessageSource with convention `problemDetail.detail.<ExceptionFQN>`. | 9 |
| 2 | https://www.baeldung.com/problem-details-spring-boot | ProblemDetail with Spring Boot | Hands-on guide for ProblemDetail. Shows `ProblemDetail.forStatusAndDetail()`, `setProperty()` for extensions, `Content-Type: application/problem+json`. Custom `@ExceptionHandler` for domain exceptions. | 8 |
| 3 | https://www.rfc-editor.org/rfc/rfc9457 | RFC 9457 — Problem Details for HTTP APIs | `type` (URI reference), `title` (short summary), `status` (HTTP code), `detail` (human-readable, i18n-able), `instance` (occurrence URI). Extensions via additional members. `detail` SHOULD be localized if `Content-Language` is set. | 10 |
| 4 | https://stackoverflow.com/questions/tagged/spring-messagesource | Spring MessageSource Q&A | `MessageFormat {0}` interpolation, `ReloadableResourceBundleMessageSource` for hot reload, custom `AbstractMessageSource` for DB-backed sources. Common pitfalls: missing default locale, UTF-8 encoding. | 7 |
| 5 | https://www.masteringbackend.com/posts/api-design-best-practices | API Design Best Practices | Recommends: envelope for success (`{ success, data, meta }`), RFC 7807/9457 for errors. Separation allows different Content-Type headers. Client determines format via HTTP status code. | 7 |

**Takeaways Iteration 2:**
- RFC 9457 spec explicitly states `detail` SHOULD be localized when `Content-Language` is set — validates our approach
- Spring Boot 3.x convention `problemDetail.detail.<FQCN>` allows zero-code i18n for standard exceptions
- For custom exceptions (like `AuthException`), manual `MessageSource.getMessage()` in `@ExceptionHandler` is the standard pattern
- `AbstractMessageSource.resolveCode()` is the extension point for DB-backed implementations — exactly what our `DatabaseMessageSource` does
- UTF-8 encoding for `.properties` files is critical — `setDefaultEncoding("UTF-8")` on `ReloadableResourceBundleMessageSource`

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"server-side vs client-side error message i18n REST API"` (từ gap trong Iter 1) | Industry consensus: server-side for API messages (single source of truth), client-side for UI text (labels, buttons). Server approach: `Accept-Language` → `MessageSource` → rendered message in response. | ✅ |
| 2 | `"database backed message source spring boot caffeine cache"` (deep dive DB approach) | Pattern: extend `AbstractMessageSource`, override `resolveCode()`, add Caffeine/Redis cache with TTL. Common TTL: 5-15 minutes. Invalidation via cache eviction endpoint or event. | ✅ |
| 3 | `"X-App-Version X-Client-Platform custom headers API"` (client metadata) | Standard practice for mobile/web apps. Used for: audit logging, feature flags, force-update notifications, A/B testing. No official RFC — convention is `X-` prefix (deprecated) or custom without prefix. | ✅ |
| 4 | `"Content-Language response header REST API i18n"` (verify standard) | RFC 7231 §3.1.3.2: `Content-Language` indicates language of intended audience. MUST set when response is localized. Value matches `Accept-Language` quality negotiation result. | ✅ |

**Stop reason**: All research questions answered, diminishing returns on further search.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Spec | RFC 9457 — Problem Details for HTTP APIs | https://www.rfc-editor.org/rfc/rfc9457 | Standard error format; `detail` field designed for i18n | 10 | Industry standard, machine-readable | Requires custom extension for error codes |
| 2 | Docs | Spring MVC Error Responses | https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html | Native ProblemDetail + MessageSource i18n | 9 | Zero-dep, built-in to Boot 3.x | Auto-convention only for standard exceptions |
| 3 | Guide | Baeldung — ProblemDetail Spring Boot | https://www.baeldung.com/problem-details-spring-boot | Hands-on ProblemDetail setup, custom handlers | 8 | Practical examples | Doesn't cover DB-backed MessageSource |
| 4 | Spec | RFC 7231 §3.1.3.2 — Content-Language | https://www.rfc-editor.org/rfc/rfc7231#section-3.1.3.2 | Standard for `Content-Language` response header | 8 | Standard compliance | N/A |
| 5 | Blog | API Design Best Practices (MasteringBackend) | https://www.masteringbackend.com/posts/api-design-best-practices | Dual format pattern: envelope + ProblemDetail | 7 | Real-world patterns | General, not Spring-specific |
| 6 | Docs | Spring MessageSource Reference | https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-messagesource | MessageSource interface, bundle resolution, interpolation | 8 | Official docs | Verbose |
| 7 | Q&A | StackOverflow — MessageSource patterns | https://stackoverflow.com/questions/tagged/spring-messagesource | Practical solutions for MessageFormat, encoding, DB source | 7 | Real-world issues | Fragmented across answers |
| 8 | Spec | RFC 7231 §5.3.5 — Accept-Language | https://www.rfc-editor.org/rfc/rfc7231#section-5.3.5 | Client locale preference with quality values | 8 | Standard | N/A |
| 9 | Blog | Various mobile API patterns | Multiple sources | `X-App-Version`, `X-Client-Platform` for audit + feature flags | 6 | Practical | No standard spec |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Spring Boot 3.x (native ProblemDetail) | Built-in `ProblemDetail` + `MessageSource` + `AcceptHeaderLocaleResolver` | RFC 9457 support, auto message resolution, locale context | Zero dependency, framework-native | Already in tech stack, well-documented | Must implement DB MessageSource manually | No DB-backed source out of box |
| error-handling-spring-boot-starter | Auto annotation-driven exception → ProblemDetail mapping | `@ResponseErrorCode`, `@ResponseErrorProperty`, auto MessageSource lookup | Reduces boilerplate | Clean annotation API | Different convention than existing `ErrorCodeBase`, no DB source | Convention conflict, adds dependency |

### So sánh tính năng chi tiết

| Feature | Spring Boot 3.x (native) | error-handling-starter | Custom Build (current) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| RFC 9457 ProblemDetail | ✅ | ✅ | ✅ | ⭐ Must |
| MessageSource i18n | ✅ | ✅ | ✅ | ⭐ Must |
| Accept-Language resolution | ✅ | ✅ | ✅ | ⭐ Must |
| Content-Language response | ✅ (manual) | ❌ | ✅ | ⭐ Must |
| Database-backed messages | ❌ | ❌ | ✅ | ⭐ Must |
| Caffeine cache for DB source | ❌ | ❌ | ✅ | ⭐ Must |
| Custom error code mapping | ✅ (manual) | ✅ (annotation) | ✅ | ⭐ Must |
| Message interpolation | ✅ | ✅ | ✅ | ⭐ Must |
| Success response wrapper | ❌ | ❌ | ✅ (`ApiResponse<T>`) | ⭐ Must |
| Retry-After header | ✅ (manual) | ❌ | ✅ | Nice to have |
| Extension properties | ✅ | ✅ | ✅ | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | Server-side i18n (MessageSource) | Server renders final message using `MessageSource.getMessage(code, args, locale)`. Client shows `detail`/`message` directly. | Single source of truth, consistent across all clients, no duplicate logic | Server must maintain message bundles, slightly larger response | Multiple clients (web, mobile, desktop) sharing same API | RFC 9457, Spring docs |
| 2 | Client-side i18n (error code only) | Server returns error code + params. Client maps code → localized message template and interpolates. | Server is language-agnostic, smaller response | Duplicate message logic per client, client must know business context | Single client platform, simple error types | Various blogs |
| 3 | Hybrid (code + rendered message) | Server returns both: `errorCode` (machine-readable) + `detail` (rendered message). Client uses `errorCode` for logic, `detail` for display. | Best of both worlds — client has code for logic AND rendered message for display | Slightly redundant data | Complex clients needing both logic branching AND display — **THIS IS OUR PATTERN** | RFC 9457 (detail + type fields) |
| 4 | Database-backed MessageSource | Extend `AbstractMessageSource`, query DB for messages, cache with Caffeine/Redis. Allows runtime message updates without redeployment. | Runtime message management, no redeploy for text changes | DB dependency, cache invalidation complexity | Enterprise systems, admin-managed messages — **ALREADY IMPLEMENTED** | Spring docs, custom implementations |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Khmer (`km`) translation quality/completeness | ✅ | Requires native speaker review — cannot be verified via research | Low — structure is in place, only content needs translation |
| 2 | `BaseControllerAdvice` (base-core) MessageSource injection backward compatibility | ✅ | Requires testing across all services using base-core | Medium — mitigated by nullable injection pattern |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-11
> **Next step**: Comparison Analysis (comparison_analysis.md)
