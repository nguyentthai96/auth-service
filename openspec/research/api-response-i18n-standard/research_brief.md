# Research Brief: API Response & i18n Standard

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | api-response-i18n-standard |
| **Ngày tạo** | 2026-08-11 |
| **Input source** | Name — tên tính năng + mô tả |
| **Input content** | Chuẩn hóa format API response (success + error) với server-side i18n message rendering, unified response contract, client metadata headers |
| **Người yêu cầu** | System (pipeline auto) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Hệ thống hiện tại có 3 vấn đề chính:

1. **Client đang build message từ error params** — Frontend hard-code logic compose message từ error fields (`maxSessions`, `dimension`, v.v.), dẫn đến duplicate code giữa web/mobile/desktop và không hỗ trợ đa ngôn ngữ.
2. **Dual response format không thống nhất** — Success dùng `ApiResponse<T>`, auth error dùng RFC 7807 `ProblemDetail`, base error dùng `ApiResponse<Unit>`. Client phải handle 2+ format khác nhau.
3. **Không có cơ chế i18n server-side** — Server trả message tiếng Anh hard-code, không đọc `Accept-Language` header, không có `MessageSource` / message bundles.

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Server render message hoàn chỉnh (đã interpolate params) theo ngôn ngữ client request
- [x] Objective 2: Client chỉ cần show `message`/`detail` field — không cần switch/case để build string
- [x] Objective 3: Unified response contract — Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457)
- [x] Objective 4: Client gửi metadata headers — `Accept-Language`, `X-App-Version`, `X-Client-Platform`
- [x] Objective 5: Dual i18n strategy — Server cho API messages, Frontend cho UI text

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Server-side i18n message rendering (Spring `MessageSource`) | Real-time translation service (e.g., Google Translate API) |
| `Accept-Language` header locale resolution | Dynamic locale creation (admin UI for adding new locales) |
| Message bundles (`.properties` files) for `en`, `vi` | Khmer (`km`) translation content (structure only) |
| `ProblemDetail` (RFC 9457) for error responses | Changing base-core `ApiResponse<T>` structure |
| Client metadata headers (`X-App-Version`, `X-Client-Platform`) | Client-side i18n overhaul (react-i18next existing setup) |
| `Content-Language` response header | Pagination/filtering i18n |
| Database-backed `DatabaseMessageSource` for runtime message management | Admin CRUD UI for i18n messages |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords

- `RFC 9457 ProblemDetail`
- `Spring Boot MessageSource`
- `server-side i18n REST API`
- `Accept-Language locale resolution`
- `API response envelope format`

### 3.2 Secondary Keywords

- `Content-Language response header`
- `message interpolation MessageFormat`
- `ReloadableResourceBundleMessageSource`
- `AcceptHeaderLocaleResolver`
- `error message internationalization microservices`

### 3.3 Domain-Specific Terms

- `ProblemDetail (RFC 9457)`: Standard error response format for HTTP APIs — replaces ad-hoc error JSON structures
- `MessageSource`: Spring interface for resolving messages with i18n support — `getMessage(code, args, locale)`
- `ApiResponse<T>`: Base-core's success response wrapper — `{code, msgCode, message, data, timestamp}`
- `ErrorCodeBase`: Base-core enum base class — carries `code`, `msgCode`, `description` per error type
- `DatabaseMessageSource`: Custom `AbstractMessageSource` backed by `i18n_messages` table with Caffeine cache

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"RFC 9457 ProblemDetail i18n server-side message rendering"` | Error format | High |
| 2 | `"Spring Boot MessageSource Accept-Language locale resolution"` | i18n mechanism | High |
| 3 | `"API response envelope standard unified format REST"` | Response design | High |
| 4 | `"server-side error message interpolation vs client-side i18n"` | Architecture decision | Medium |
| 5 | `"Spring Boot i18n database message source example"` | DB-backed messages | Medium |
| 6 | `"error-handling-spring-boot-starter i18n"` | Open source | Medium |
| 7 | `"API versioning client metadata headers best practices"` | Client headers | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| `ApiResponse<T>` wrapper | `base-core/.../payload/ApiResponse.kt` | High | Success response format — has `errorLang()` factory method (unused) |
| `ErrorCodeBase` enum | `base-core/.../exception/base/ErrorCodeBase.kt` | High | Each error has `code`, `msgCode`, `description` — `withArgs()` for interpolation |
| `BaseControllerAdvice` | `base-core/.../web/BaseControllerAdvice.kt` | High | Global exception handler — returns `ApiResponse<Unit>` for `BusinessException` |
| `AuthControllerAdvice` | `auth-service/.../GlobalExceptionHandler.kt` | High | Auth-specific — returns `ProblemDetail` with `MessageSource` integration (ALREADY IMPLEMENTED) |
| `AuthErrorCode` enum | `auth-service/.../AuthErrorCode.kt` | High | 30 error codes (AUTH_001→AUTH_044) with `msgCode` convention (`auth.<snake_case>`) |
| `I18nConfig` | `auth-service/.../shared/config/I18nConfig.kt` | High | CompositeMessageSource: DatabaseMessageSource → file bundles (ALREADY IMPLEMENTED) |
| `DatabaseMessageSource` | `auth-service/.../shared/i18n/DatabaseMessageSource.kt` | High | DB-backed MessageSource with Caffeine cache, 5-min TTL (ALREADY IMPLEMENTED) |
| `I18nMessageEntity` | `auth-service/.../shared/i18n/I18nMessageEntity.kt` | High | JPA entity for `i18n_messages` table (ALREADY IMPLEMENTED) |
| Message bundles | `src/main/resources/messages/auth-messages*.properties` | High | `en` and `vi` bundles with 30+ message keys (ALREADY IMPLEMENTED) |
| `i18n.ts` | `admindashboard/src/@i18n/i18n.ts` | Medium | react-i18next setup (EN only, placeholder) |
| `api.ts` | `admindashboard/src/utils/api.ts` | Medium | ky HTTP client with `globalHeaders` hook |

### 4.2 Existing Code Patterns

- `AuthControllerAdvice` **already injects** `MessageSource` and resolves i18n messages via `resolveMessage()` method
- `extractMessageArgs()` maps exception properties to `MessageFormat {0}, {1}` placeholders
- `Content-Language` header **already set** via `setContentLanguageHeader()` method
- `I18nConfig` provides composite resolution: DatabaseMessageSource (Caffeine-cached) → file bundles
- `ErrorCodeBase.msgCode` convention matches message bundle keys exactly (e.g., `auth.rate_limited`)
- `ApiResponse.errorLang()` factory method exists but is **not wired** to `MessageSource`
- Frontend `setGlobalHeaders()` utility exists in `api.ts` — can inject `Accept-Language`

### 4.3 Tech Stack Constraints

- Language: Kotlin 1.9+
- Framework: Spring Boot 3.x
- Database: PostgreSQL (via JPA/Hibernate)
- Cache: Caffeine (in-process) + Redis (rate limiting)
- Build tool: Gradle (Kotlin DSL)
- Base library: base-core (shared across microservices)
- Frontend: React 19 + Vite + ky HTTP client
- Frontend i18n: react-i18next (i18next)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `MessageSource` | Spring bean | `I18nConfig.kt` | CompositeMessageSource (DB + file) — already configured |
| `BaseControllerAdvice` | Base class | `base-core` | Auth-service extends this — shared across services |
| `AuthControllerAdvice` | Exception handler | `GlobalExceptionHandler.kt` | Already uses `MessageSource` for i18n |
| `i18n_messages` table | Database | `I18nMessageEntity.kt` | Runtime message storage — already created |
| `auth-messages*.properties` | Classpath resource | `src/main/resources/messages/` | File-based fallback — `en` + `vi` present |
| `api.ts` | HTTP client config | `admindashboard/src/utils/api.ts` | `beforeRequest` hook for global headers |
| `I18nProvider.tsx` | React context | `admindashboard/src/@i18n/` | Language switch syncs `Accept-Language` |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: Industry standard format cho error response (RFC 9457 vs custom envelope)?
- [x] Q2: Server-side vs client-side message rendering — best practice?
- [x] Q3: How does Spring Boot 3.x native ProblemDetail support i18n?
- [x] Q4: Database-backed vs file-backed MessageSource — pros/cons?
- [x] Q5: How to handle message bundle fallback chain (DB → file → default)?
- [x] Q6: Best practice for client metadata headers (Accept-Language, X-App-Version)?
- [x] Q7: Open source projects solving similar API response i18n patterns?

### 5.2 Assumptions cần verify

- [x] A1: base-core `BaseControllerAdvice` accepts `MessageSource` injection (nullable, backward compatible) — VERIFIED: auth-service already extends and injects
- [x] A2: `ErrorCodeBase.msgCode` values match message bundle keys — VERIFIED: confirmed in `AuthErrorCode` + `auth-messages.properties`
- [x] A3: Frontend ky client supports global header injection — VERIFIED: `setGlobalHeaders()` exists

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive understanding of API i18n patterns | ≥ 5 sources |
| Open source options | Evaluated alternative approaches/libraries | ≥ 3 repos evaluated |
| Gap analysis | Current system vs target state gaps identified | All critical gaps identified |
| Business analysis | Use cases documented with flows | All UCs documented |
| Technical spec | Agent-ready specification | Component list + sequence diagrams |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
