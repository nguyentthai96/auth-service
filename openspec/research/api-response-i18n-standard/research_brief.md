# Research Brief: API Response & i18n Standard

## Feature Name
`api-response-i18n-standard`

## Problem Statement

Hiện tại hệ thống có 2 vấn đề chính:

### 1. Client đang build message từ error params
```typescript
// ❌ Anti-pattern hiện tại
setErrorMessage(`Maximum sessions (${problem.maxSessions}) reached. An existing session was revoked.`);
setErrorMessage(`Too many login attempts (${problem.dimension}). Please wait.`);
```
- Message được **hard-code** ở frontend
- Không hỗ trợ đa ngôn ngữ
- Client phải biết business logic để compose message
- Mỗi client (web, mobile, desktop) phải duplicate cùng message template

### 2. Dual response format không thống nhất
- **Success**: `ApiResponse<T>` (base-core) với `code`, `msgCode`, `message`, `data`
- **Error (auth)**: RFC 7807 `ProblemDetail` với `errorCode`, `detail`, custom properties
- **Error (base)**: `ApiResponse<Unit>` với `code`, `msgCode`, `message`
- Client phải handle 2+ response format khác nhau

### 3. Không có cơ chế i18n server-side
- Server trả message tiếng Anh hard-code
- Không có `MessageSource` / message bundles
- Không đọc `Accept-Language` header
- Client i18n (react-i18next) chỉ cho UI text, không cho API messages

## Target State

1. **Server renders message hoàn chỉnh** (đã interpolate params) theo ngôn ngữ client request
2. **Client chỉ cần show `message` field** — không cần switch/case để build string
3. **Unified response format** — 1 format cho cả success và error
4. **Client gửi metadata headers** — `Accept-Language`, `X-App-Version`, `X-Client-Platform`
5. **Frontend i18n riêng** cho UI text (button, label, placeholder) — không dùng server message

## Search Keywords

- RFC 9457 ProblemDetail i18n
- Spring Boot MessageSource server-side rendering
- API response envelope format standard
- Accept-Language header locale resolution
- Server-side error message interpolation
- Unified API response wrapper microservices
- Client version header negotiation

## Current System Analysis

### 4.1 Related Features (Existing)

| Component | Path | Pattern |
|-----------|------|---------|
| `ApiResponse<T>` | `base-core/.../payload/ApiResponse.kt` | Success wrapper: `{code, msgCode, message, data, timestamp}` |
| `ApiResponse.errorLang()` | Same file | **Đã có** factory method nhận `lang` + `msgResolver` callback |
| `ErrorCodeBase` | `base-core/.../exception/base/ErrorCodeBase.kt` | Mỗi error có `code`, `msgCode`, `description` |
| `ErrorCodeBase.withArgs()` | Same file | `description.format(*args)` — interpolation sẵn |
| `BaseControllerAdvice` | `base-core/.../web/BaseControllerAdvice.kt` | Global exception handler trả `ApiResponse<Unit>` |
| `AuthControllerAdvice` | `auth-service/.../GlobalExceptionHandler.kt` | Override cho `AuthException` → trả `ProblemDetail` |
| `AuthErrorCode` | `auth-service/.../AuthErrorCode.kt` | 21 error codes, mỗi cái có `msgCode` (e.g., `auth.rate_limited`) |
| `i18n.ts` | `admindashboard/src/@i18n/i18n.ts` | react-i18next setup (chỉ EN, placeholder) |
| `api.ts` | `admindashboard/src/utils/api.ts` | ky client với `globalHeaders` hook |

### 4.2 Existing Patterns

- `ApiResponse.errorLang()` **đã support i18n concept** nhưng chưa được sử dụng
- `ErrorCodeBase.withArgs()` **đã support interpolation** (`description.format(*args)`)
- `AuthControllerAdvice` trả `ProblemDetail` thay vì `ApiResponse` → inconsistent
- Frontend `setGlobalHeaders()` utility sẵn — dễ inject `Accept-Language`

### 4.3 Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend framework | Spring Boot 3.x (Kotlin) |
| Base library | base-core (shared across microservices) |
| Frontend framework | React 19 + Vite |
| Frontend i18n | react-i18next (i18next) |
| HTTP client | ky |
| Response format | `ApiResponse<T>` (success) + `ProblemDetail` (auth error) |
| Message bundles | **NONE** (chưa có `messages.properties`) |
