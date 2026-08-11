## Why

API responses hiện tại sử dụng hardcoded English messages — không hỗ trợ đa ngôn ngữ. Frontend JwtSignInForm.tsx có ~12 hardcoded error message strings, tự build message từ error code (switch/case). Khi mở rộng ngôn ngữ hoặc thêm error mới, phải sửa cả backend lẫn frontend. Cần chuẩn hóa: server render message i18n hoàn chỉnh → client chỉ hiển thị `detail`/`message` field.

## What Changes

- **Server-side i18n**: Spring `MessageSource` resolve message theo `Accept-Language` header cho tất cả API responses
- **Dual message source**: Common/static codes → `.properties` files, dynamic messages → database table `i18n_messages` (quản lý runtime)
- **Locale resolution**: `AcceptHeaderLocaleResolver` (Spring built-in) — supported: `en` (default), `vi`
- **Frontend auto-detect**: Hybrid Cascade (`localStorage` → `navigator.languages` → `en` fallback)
- **base-core refactor**: `BaseControllerAdvice` inject `MessageSource` (nullable, backward compatible)
- **ApiResponse.success() i18n**: Success message cũng cần đa ngôn ngữ
- **ProblemDetail i18n**: `AuthControllerAdvice` resolve `detail` field qua `MessageSource`
- **Frontend cleanup**: Remove hardcoded error messages, hiển thị trực tiếp `problem.detail`
- **Client metadata headers**: `Accept-Language`, `X-App-Version`, `X-Client-Platform`
- **Content-Language response header**: Server trả ngôn ngữ thực tế đã dùng
- **Frontend languages update**: Remove `tr`/`ar`, thêm `vi` vào I18nProvider

## Capabilities

### New Capabilities
- `i18n-message-resolution`: Server resolve message đa ngôn ngữ theo Accept-Language header, fallback chain: locale bundle → default bundle → ErrorCodeBase.description
- `database-message-source`: Dynamic i18n messages lưu trong DB table `i18n_messages`, loadable tại runtime, cache Caffeine, invalidate khi update
- `locale-auto-detection`: Frontend detect ngôn ngữ browser → match supported locales → persist localStorage → sync Accept-Language header
- `client-metadata-headers`: Frontend inject X-App-Version, X-Client-Platform headers cho audit/logging

### Modified Capabilities
- `error-handling`: BaseControllerAdvice + AuthControllerAdvice → resolve i18n messages thay vì hardcoded English
- `api-response`: ApiResponse.success() → accept i18n message parameter
- `frontend-auth`: JwtSignInForm.tsx → hiển thị problem.detail trực tiếp, không build string
- `frontend-i18n`: I18nProvider → update languages list (en, vi), sync Accept-Language header

## Impact

### Backend (base-core)
- **MODIFY**: `BaseControllerAdvice` (inject MessageSource nullable), `ApiResponse` (success i18n overload), `BaseCoreServletAutoConfiguration` (locale/message beans)
- **NEW**: `I18nAutoConfiguration` (MessageSource + LocaleResolver beans), `messages.properties` (en), `messages_vi.properties` (vi)

### Backend (auth-service)
- **MODIFY**: `AuthControllerAdvice` (resolve ProblemDetail.detail via MessageSource), `CqrsAuthController` (i18n success messages)
- **NEW**: `messages.properties` (21 auth error codes en), `messages_vi.properties` (21 auth error codes vi), `V5__create_i18n_messages.sql` (DB table), `I18nMessageEntity`, `I18nMessageRepository`, `DatabaseMessageSource`

### Frontend (admindashboard)
- **MODIFY**: `api.ts` (inject headers), `I18nProvider.tsx` (languages list + header sync), `i18n.ts` (language detector + vi), `JwtSignInForm.tsx` (remove hardcoded messages)

### Database
- **NEW**: `i18n_messages` table (code, locale, message, module, is_active, created_at, updated_at)
