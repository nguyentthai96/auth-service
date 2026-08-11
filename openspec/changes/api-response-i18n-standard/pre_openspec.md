# Pre-OpenSpec: api-response-i18n-standard

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (Feature Research Output)
> **Classification Evidence**: `errorLang` → `ApiResponse.kt` → `base-core/.../payload/ApiResponse.kt`; `BaseControllerAdvice` → `base-core/.../web/BaseControllerAdvice.kt`
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Chuẩn hóa format API request/response giữa client và server: Server render message i18n hoàn chỉnh (đã interpolate params) theo ngôn ngữ client yêu cầu qua `Accept-Language` header. Client chỉ hiển thị `message`/`detail` field — không tự build string. Dual response format: Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457). Client gửi metadata headers (`Accept-Language`, `X-App-Version`, `X-Client-Platform`). Frontend duy trì i18n riêng (react-i18next) cho UI text.

| Metric | Giá trị |
|--------|---------|
| Số FR | 13 (Idea: 9, Enriched: 4) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 1 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Admin User**: Người dùng admin dashboard, gửi request với ngôn ngữ ưa thích
- **Frontend App**: React SPA, inject metadata headers, hiển thị message từ server
- **Backend Server**: Spring Boot service, resolve locale, render message i18n, trả response thống nhất
- **base-core Library**: Shared library cung cấp `ApiResponse`, `BaseControllerAdvice`, `MessageSource` config

## 2. Functional Requirements

### FR-001: Server render message đa ngôn ngữ [IDEA]
- **Actor**: Backend Server
- **Action**: Hệ thống phải render message hoàn chỉnh (đã interpolate params) bằng ngôn ngữ client request khi trả error response
- **Validation**: Message chứa giá trị đã interpolate (ví dụ: "Đã đạt tối đa 3 phiên" thay vì template)

### FR-002: Locale resolution từ Accept-Language header [IDEA]
- **Actor**: Backend Server
- **Action**: Hệ thống phải đọc `Accept-Language` header để resolve locale khi xử lý request
- **Validation**: `Accept-Language: vi-VN` → locale = `vi`; missing header → fallback `en`

### FR-003: Message bundle infrastructure [IDEA]
- **Actor**: Backend Server
- **Action**: Hệ thống phải cấu hình `MessageSource` với resource bundles cho mỗi ngôn ngữ được hỗ trợ
- **Validation**: `messages.properties` (en), `messages_vi.properties` (vi) tồn tại và loadable

### FR-004: Error response dùng ProblemDetail với i18n detail [IDEA]
- **Actor**: Backend Server
- **Action**: Hệ thống phải trả error response dạng `ProblemDetail` (RFC 9457) với `detail` field là message đã i18n
- **Validation**: Error response có `Content-Type: application/problem+json`, `detail` bằng ngôn ngữ request

### FR-005: Success response dùng ApiResponse với i18n message [IDEA]
- **Actor**: Backend Server
- **Action**: Hệ thống phải trả success response dạng `ApiResponse<T>` với `message` field là message đã i18n
- **Validation**: Success response có `message` bằng ngôn ngữ request

### FR-006: Content-Language response header [IDEA]
- **Actor**: Backend Server
- **Action**: Hệ thống phải set `Content-Language` response header cho biết ngôn ngữ thực tế của message
- **Validation**: Response header chứa `Content-Language: vi` khi resolve locale = `vi`

### FR-007: Client gửi Accept-Language header [IDEA]
- **Actor**: Frontend App
- **Action**: Client phải gửi `Accept-Language` header trong mọi API request, sync với ngôn ngữ user đã chọn
- **Validation**: Đổi language → header thay đổi → API response đổi ngôn ngữ

### FR-008: Client gửi X-App-Version header [IDEA]
- **Actor**: Frontend App
- **Action**: Client phải gửi `X-App-Version` header chứa semantic version của app
- **Validation**: Header present trong mọi request, format `x.y.z`

### FR-009: Client hiển thị message trực tiếp từ server [IDEA]
- **Actor**: Frontend App
- **Action**: Client phải hiển thị `problem.detail` (error) hoặc `response.message` (success) trực tiếp, không tự build string
- **Validation**: Không còn hardcoded error message template trong frontend code

### FR-010: Fallback chain khi thiếu translation [ENRICHED]
- **Actor**: Backend Server
- **Action**: Hệ thống phải implement fallback chain: `messages_{lang}` → `messages` (default) → `ErrorCodeBase.description` khi message key không tìm thấy
- **Validation**: Missing key không gây exception, trả fallback message

### FR-011: Client metadata header X-Client-Platform [ENRICHED]
- **Actor**: Frontend App
- **Action**: Client phải gửi `X-Client-Platform` header để server audit/logging
- **Validation**: Header = `web` cho admin dashboard

### FR-012: Supported locales whitelist [ENRICHED]
- **Actor**: Backend Server
- **Action**: Hệ thống phải giới hạn supported locales (en, vi) và reject/fallback unsupported locale
- **Validation**: `Accept-Language: ja` → fallback `en`

### FR-013: Idempotent locale resolution [ENRICHED]
- **Actor**: Backend Server
- **Action**: Hệ thống phải đảm bảo locale resolution idempotent — cùng header luôn trả cùng ngôn ngữ
- **Validation**: Multiple requests với cùng `Accept-Language` → cùng `Content-Language`

## 3. Non-functional Requirements

- NFR-001: Message bundle loading không ảnh hưởng startup time (< 100ms overhead)
- NFR-002: `MessageSource.getMessage()` latency < 1ms per call (in-memory)
- NFR-003: Backward compatible — existing API consumers không bị break nếu không gửi `Accept-Language`

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-010**: Fallback chain — domain requirement để tránh NPE/missing translation crash
- **FR-011**: Client platform header — chuẩn audit logging practice
- **FR-012**: Locale whitelist — bảo mật, tránh resource exhaustion attack qua fake locale
- **FR-013**: Idempotent resolution — deterministic behavior requirement

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Spring MessageSource | i18n message resolution | Built-in, không cần external dependency |
| react-i18next | Frontend UI text i18n | Đã setup sẵn, chỉ cần sync language |

## 6. Assumptions

- A1: ~~Supported locales ban đầu: `en` (default), `vi`. Có thể mở rộng thêm `km` sau.~~ → **CONFIRMED**: Supported locales: `en` (default), `vi`. Format BCP 47. Auto-detect: Hybrid Cascade (`localStorage` → `navigator.languages` → `en` fallback).
- A2: Message bundle nằm trong classpath (không dùng external DB/remote source)
- A3: `X-App-Version` ban đầu đọc từ `package.json` version hoặc env variable
- A4: `BaseControllerAdvice` refactor backward compatible — `MessageSource` inject optional (nullable)
- A5: Frontend languages list cần update: remove `tr`/`ar`, add `vi` (hiện tại có `en`, `tr`, `ar` — sai)

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-008: "semantic version" cần define format cụ thể |
| Đầy đủ (Completeness) | 22/25 | FR-003: chưa specify encoding (UTF-8 implicit) |
| Nhất quán (Consistency) | 25/25 | — |
| Kiểm thử được (Testability) | 18/25 | FR-005: "i18n message" cần example value để test; FR-013: hard to verify "idempotent" |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -2 | FR-008 | "semantic version" chưa define regex | Thêm pattern `^\d+\.\d+\.\d+$` |
| 2 | Completeness | -3 | FR-003 | Encoding, file location chưa explicit | Thêm "UTF-8, classpath:messages/" |
| 3 | Testability | -4 | FR-005 | "i18n message" thiếu expected value | Thêm example: "Đăng nhập thành công" |
| 4 | Testability | -3 | FR-013 | "idempotent" khó verify automated | Thêm: "given same header, N calls → same Content-Language" |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | `BaseControllerAdvice` là shared library — refactor ảnh hưởng tất cả services dùng base-core | FR-001 | Inject `MessageSource` optional (nullable), fallback existing behavior |
| 2 | Risk | 🟡 | Frontend hardcoded messages cần cleanup — nếu thiếu sót sẽ inconsistent | FR-009 | Grep toàn bộ `setErrorMessage` calls, verify tất cả dùng server message |

## 9. Open Questions

- ~~OQ-1: Có cần thêm locale `km` (Khmer) ngay từ phase đầu hay để mở rộng sau?~~ → **RESOLVED**: Chỉ `en` (default) + `vi`. Dùng locale chuẩn quốc tế BCP 47 (RFC 5646). Mở rộng sau. Auto-detect qua `navigator.languages` (browser/OS locale), persist localStorage, KHÔNG dùng IP geolocation.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Authentication & Authorization (auth-service)
- Shared Library (base-core)
- Admin Dashboard Frontend (admindashboard)

### 10.2 Flow Type
Command — Cross-cutting concern (i18n message resolution), applies to all endpoints.

### 10.3 Candidate Services
- **base-core**: Foundation — `ApiResponse`, `BaseControllerAdvice`, `ErrorCodeBase` cần extend thêm `MessageSource` integration
- **auth-service**: `AuthControllerAdvice` cần refactor để dùng `MessageSource` cho `ProblemDetail.detail`
- **admindashboard**: Frontend cần inject headers và simplify error message handling

### Detection Evidence
- Keyword: `errorLang` → Module: `base-core/domain/web/payload` → File: `ApiResponse.kt`
- Keyword: `BaseControllerAdvice` → Module: `base-core/domain/web` → File: `BaseControllerAdvice.kt`
- Keyword: `AuthControllerAdvice` → Module: `auth-service/shared/exception` → File: `GlobalExceptionHandler.kt`
- Keyword: `AuthErrorCode.msgCode` → Module: `auth-service/shared/exception` → File: `AuthErrorCode.kt`
- Keyword: `setGlobalHeaders` → Module: `admindashboard/src/utils` → File: `api.ts`

### 10.4 External Integrations
- Spring `MessageSource` (built-in framework feature)
- react-i18next (đã setup sẵn)

### 10.5 Required Modules
- `base-core/configuration` — MessageSource config, Locale config
- `base-core/domain/web` — BaseControllerAdvice refactor
- `auth-service/shared/exception` — AuthControllerAdvice refactor
- `auth-service/resources` — message bundles
- `admindashboard/src/utils` — api.ts header injection
- `admindashboard/src/@i18n` — language sync
- `admindashboard/src/@auth` — error message simplification

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Frontend App | User chọn ngôn ngữ (ví dụ: vi) | react-i18next |
| 2 | Frontend App | Inject `Accept-Language: vi-VN` + `X-App-Version` + `X-Client-Platform` vào global headers | ky HTTP client |
| 3 | Frontend App | Gửi API request | ky → Server |
| 4 | Backend Server | `AcceptHeaderLocaleResolver` resolve locale từ header | Spring MVC |
| 5 | Backend Server | Controller/Service xử lý business logic | auth-service |
| 6a | Backend Server | (Success) `MessageSource.getMessage(key, args, locale)` → build `ApiResponse` | base-core |
| 6b | Backend Server | (Error) Exception → `ControllerAdvice` → `MessageSource.getMessage(msgCode, args, locale)` → build `ProblemDetail` | base-core + auth-service |
| 7 | Backend Server | Set `Content-Language` response header | Spring MVC |
| 8 | Frontend App | (Success) Show `response.message` trực tiếp | React UI |
| 9 | Frontend App | (Error) Show `problem.detail` trực tiếp, use `errorCode` cho client logic | React UI |

## 12. Traceability Matrix

| FR-ID | Source Section | Spec Section | Affected Class | Status |
|-------|--------------|-------------|---------------|--------|
| FR-001 | Research: UC-001 | TBD | `BaseControllerAdvice`, `AuthControllerAdvice` | Mapped |
| FR-002 | Research: UC-001 | TBD | `LocaleConfig` [NEW] | Mapped |
| FR-003 | Research: UC-001 | TBD | `MessageSourceConfig` [NEW], `messages_*.properties` [NEW] | Mapped |
| FR-004 | Research: UC-001 | TBD | `AuthControllerAdvice` [MODIFY] | Mapped |
| FR-005 | Research: UC-003 | TBD | `CqrsAuthController` [MODIFY] | Mapped |
| FR-006 | Research: UC-001 | TBD | `BaseControllerAdvice` [MODIFY] | Mapped |
| FR-007 | Research: UC-002 | TBD | `api.ts` [MODIFY], `I18nProvider.tsx` [MODIFY] | Mapped |
| FR-008 | Research: UC-002 | TBD | `api.ts` [MODIFY] | Mapped |
| FR-009 | Research: UC-001 | TBD | `JwtSignInForm.tsx` [MODIFY], `SignInPageForm.tsx` [MODIFY] | Mapped |
| FR-010 | Enriched | TBD | `BaseControllerAdvice` [MODIFY] | Mapped |
| FR-011 | Enriched | TBD | `api.ts` [MODIFY] | Mapped |
| FR-012 | Enriched | TBD | `LocaleConfig` [NEW] | Mapped |
| FR-013 | Enriched | TBD | `LocaleConfig` [NEW] | Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
Feature này thuộc dạng **cross-cutting concern** — ảnh hưởng toàn bộ API layer nhưng thay đổi chủ yếu ở infrastructure level (config, advice, filter). Rủi ro thấp vì:
1. `ApiResponse.errorLang()` đã tồn tại → chỉ cần wire `MessageSource`
2. `ErrorCodeBase.msgCode` đã define message key convention → dùng trực tiếp
3. `AcceptHeaderLocaleResolver` là built-in Spring → zero custom code

### Related Features / Precedents
- `auth-login-admin` (archived: `openspec/changes/archive/2026-08-11-auth-login-admin/`) — triển khai `AuthControllerAdvice` + `ProblemDetail` error format. Feature này refactor lại advice để thêm i18n.

### Integration Notes
- **Spring MessageSource**: Built-in, zero external dependency. Chỉ cần `@Bean` config + `.properties` files.
- **react-i18next**: Đã setup, chỉ cần sync `i18n.language` → `Accept-Language` header.
- **Backward compatible**: Nếu client không gửi `Accept-Language` → server dùng `en` (default). Existing consumers không bị break.

### Suggested Approach
1. **base-core FIRST**: `MessageSourceConfig` + `LocaleConfig` + refactor `BaseControllerAdvice` (inject `MessageSource?` nullable)
2. **auth-service SECOND**: Message bundles + refactor `AuthControllerAdvice` 
3. **frontend LAST**: Header injection + error message simplification

### Context from Confluence Images
N/A
