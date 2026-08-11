# Business Analysis: API Response & i18n Standard

## Overview

Chuẩn hóa format request/response giữa client và server, bao gồm:
- Server-side message rendering (đa ngôn ngữ, interpolated params)
- Unified response contract (success: `ApiResponse<T>`, error: `ProblemDetail`)
- Client metadata headers (`Accept-Language`, `X-App-Version`, `X-Client-Platform`)
- Dual i18n strategy (server cho API messages, frontend cho UI text)

---

## Use Case 1: UC-001 — Server-Side Error Message Rendering

### Semantic Description
Khi server trả lỗi, message **phải được render hoàn chỉnh** (đã interpolate params) bằng ngôn ngữ mà client request. Client **chỉ cần hiển thị** `detail` / `message` field — không cần hiểu structure của error để compose message.

### Basic Flow
1. Client gửi request với `Accept-Language: vi-VN`
2. Server xử lý → throw exception (ví dụ: `SessionLimitExceededException(maxSessions=3)`)
3. `ControllerAdvice` bắt exception
4. Resolve locale từ `LocaleContextHolder` (đọc từ `Accept-Language`)
5. Lookup message bundle: `messages_vi.properties` → key `auth.session_limit`
6. Interpolate: `"Đã đạt tối đa {0} phiên đăng nhập. Phiên cũ đã bị thu hồi."` + args `[3]`
7. Build `ProblemDetail` với `detail = "Đã đạt tối đa 3 phiên đăng nhập. Phiên cũ đã bị thu hồi."`
8. Set `Content-Language: vi` response header
9. Return `ProblemDetail` JSON

### Exception Flow
- **E1**: `Accept-Language` không được gửi → fallback `en` (default)
- **E2**: Message key không tồn tại trong bundle → fallback `description` từ `ErrorCodeBase`
- **E3**: Interpolation args không khớp → log warning, dùng raw template

### Business Rules
- BR-001: Server **PHẢI** render message hoàn chỉnh — client **KHÔNG ĐƯỢC** tự compose
- BR-002: Default language = `en`, supported = `en`, `vi`, `km` (có thể mở rộng)
- BR-003: `errorCode` field vẫn giữ nguyên để client logic (route, countdown, CAPTCHA trigger)
- BR-004: Message key = `ErrorCodeBase.msgCode` (e.g., `auth.rate_limited`)

---

## Use Case 2: UC-002 — Client Metadata Headers

### Semantic Description
Mỗi request từ client gửi metadata headers để server có thể:
- Resolve đúng ngôn ngữ
- Log/audit app version
- Feature flag per platform/version
- Force update notification

### Basic Flow
1. Frontend init → đọc `i18n.language` + `APP_VERSION` + `PLATFORM`
2. Set global headers vào ky instance:
   ```
   Accept-Language: vi-VN, vi;q=0.9, en;q=0.8
   X-App-Version: 1.0.0
   X-Client-Platform: web
   ```
3. Mỗi request tự động kèm headers qua `beforeRequest` hook
4. Server đọc headers cho locale resolution + audit logging

### Exception Flow
- **E1**: Header missing → server dùng defaults (`en`, version `unknown`, platform `unknown`)

### Business Rules
- BR-005: `Accept-Language` follow RFC 7231 format với quality values
- BR-006: `X-App-Version` = semantic version (e.g., `1.2.3`)
- BR-007: `X-Client-Platform` ∈ `{web, ios, android, desktop}`

---

## Use Case 3: UC-003 — Unified Success Response Format

### Semantic Description
Tất cả success response đều wrap trong `ApiResponse<T>` thống nhất.
`message` field trong success response cũng được i18n server-side.

### Basic Flow
1. Controller xử lý thành công → gọi `ApiResponse.success(data, resolvedMessage)`
2. `resolvedMessage` = `MessageSource.getMessage("success.login", null, locale)`
3. Return:
   ```json
   {
     "code": "00",
     "msgCode": "SUCCESS",
     "message": "Đăng nhập thành công",
     "data": { ... },
     "timestamp": 1723372800000
   }
   ```

### Business Rules
- BR-008: Success response **PHẢI** dùng `ApiResponse<T>` (base-core)
- BR-009: Error response **PHẢI** dùng `ProblemDetail` (RFC 9457)
- BR-010: Client distinguish qua HTTP status code (2xx vs 4xx/5xx)

---

## Use Case 4: UC-004 — Frontend UI Text i18n (Separate)

### Semantic Description
UI text (buttons, labels, form placeholders, navigation items) được i18n riêng bằng react-i18next.
**KHÔNG** lấy từ server — static resources bundled với frontend.

### Basic Flow
1. User đổi language trong settings
2. `i18n.changeLanguage('vi')` → update all UI text
3. Set `Accept-Language: vi` vào global headers → server messages cũng đổi
4. API error/success messages hiển thị đúng ngôn ngữ user chọn

### Business Rules
- BR-011: UI text = frontend i18n resource bundles
- BR-012: API messages = server i18n (`message` / `detail` field)
- BR-013: Language switch đồng bộ cả 2 layer

---

## Traceability Matrix

| Use Case | Business Rules | Error Codes Affected | Components |
|----------|---------------|---------------------|-----------|
| UC-001 | BR-001→004 | All 21 AuthErrorCode | `BaseControllerAdvice`, `AuthControllerAdvice`, `MessageSource`, `messages_*.properties` |
| UC-002 | BR-005→007 | N/A | `api.ts`, `I18nProvider.tsx`, server audit filter |
| UC-003 | BR-008→010 | N/A | `ApiResponse`, controllers, `MessageSource` |
| UC-004 | BR-011→013 | N/A | react-i18next, `I18nProvider.tsx` |
