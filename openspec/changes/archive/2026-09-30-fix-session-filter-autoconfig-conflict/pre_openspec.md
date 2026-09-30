# Pre-OpenSpec: fix-session-filter-autoconfig-conflict

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: User Idea (Bug Investigation)
> **Classification Evidence**: DefaultSessionValidationFilter → SecurityAutoConfiguration.kt → application-security.yml
> **Archive**: openspec/changes/archive/2026-09-30-fix-security-autoconfig-bean-wiring
> **Quality Score**: 99/100

## 📋 Feature Summary

Khắc phục lỗi HTTP 401 Unauthorized khi gọi endpoint `POST /auth/login` (và các public endpoints khác) trong `auth-service`. Nguyên nhân do `DefaultSessionValidationFilter` trong `base-security-starter` được đăng ký vô điều kiện ở mức servlet filter với order `-1700` (chạy trước Spring Security filter chain ở order `-100`), tự động chặn bất kỳ request nào không có header `Authorization: Bearer` nếu path không nằm trong `publicPaths`. Giải pháp là bổ sung conditional property toggle (`app.security.session-validation.enabled`) vào `base-security-starter` và vô hiệu hóa filter này tại `auth-service`, đồng thời bổ sung cấu hình `public-paths` đầy đủ cho `auth-service`.

| Metric | Giá trị |
|--------|---------|
| Số FR | 4 (Idea: 3, Enriched: 1) |
| Issues | 1 (🔴: 0, 🟡: 1, 🟢: 0) |
| Open Questions | 0 |
| **Quality Score** | **99/100** |

---

## 1. Actors

- **End-User / Frontend Application**: Gửi request `POST /auth/login` hoặc truy cập các public endpoint mà không có Bearer token.
- **Identity Provider (auth-service)**: Xác thực thông tin đăng nhập, sinh token pair và quản lý SecurityFilterChain riêng biệt.
- **Resource Server Microservices (account-service, system-admin-service, ...)**: Tiêu thụ token do auth-service cấp phát, tiếp tục sử dụng `DefaultSessionValidationFilter` để xác thực token cục bộ.

## 2. Functional Requirements

### FR-001: Thêm Condition Toggle trong base-security-starter [IDEA]
- **Actor**: System / Base-Core Framework
- **Action**: Hệ thống phải cung cấp conditional toggle `@ConditionalOnProperty(prefix = "app.security.session-validation", name = ["enabled"], havingValue = "true", matchIfMissing = true)` cho bean `defaultSessionValidationFilter` trong `SecurityAutoConfiguration.kt`.
- **Validation**:
  - Khi `app.security.session-validation.enabled=false`, bean `defaultSessionValidationFilter` không được khởi tạo vào Spring ApplicationContext.
  - Cập nhật `SecurityProperties.kt` để ánh xạ thuộc tính `sessionValidation: SessionValidationProperties` (mặc định `enabled = true`).

### FR-002: Vô hiệu hóa Session Validation tại auth-service [IDEA]
- **Actor**: auth-service Configuration
- **Action**: Hệ thống phải cấu hình `app.security.session-validation.enabled: false` trong `application-security.yml` của `auth-service`.
- **Validation**:
  - `auth-service` không đăng ký `DefaultSessionValidationFilter` ở mức Servlet filter chain (order `-1700`).
  - Quyền kiểm soát token và whitelist endpoint hoàn toàn do `SecurityConfig` và `JwtAuthFilter` của `auth-service` đảm nhiệm.

### FR-003: Duy trì tương thích ngược cho Resource Servers [ENRICHED]
- **Actor**: Resource Server Services
- **Action**: Hệ thống phải duy trì `matchIfMissing = true` để các service downstream (`account-service`, `system-admin-service`, v.v.) không cần sửa cấu hình vẫn tự động có `DefaultSessionValidationFilter`.
- **Validation**: Các unit test và integration test hiện tại trong `base-security-starter` phải pass 100%.

### FR-004: Xác thực luồng Login và Public Endpoints thành công [IDEA]
- **Actor**: End-User / Frontend Application
- **Action**: Hệ thống phải cho phép request `POST /auth/login` không có header `Authorization` đi xuyên qua Servlet filter chain vào thẳng Spring Security và `CqrsAuthController.login`.
- **Validation**:
  - Gọi `POST /auth/login` không còn trả về lỗi `401 {"error":"Unauthorized","message":"Missing or invalid Authorization header"}`.
  - Phản hồi từ controller trả về kết quả login hợp lệ (HTTP 200 hoặc mã lỗi nghiệp vụ như sai password, thay vì 401 filter reject).

## 3. Non-functional Requirements

- **Zero Performance Overhead**: Giảm thiểu 1 tầng filter không cần thiết ở order `-1700` cho `auth-service`, cải thiện latency của request.
- **Backward Compatibility**: 100% tương thích ngược với các microservices đóng vai trò Resource Server.
- **Defense in Depth**: Cấu hình thêm `public-paths` trong `auth-service` như một lớp bảo vệ dự phòng.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

### Enriched FRs
- **FR-003**: Đảm bảo tính tương thích ngược cho downstream services thông qua `matchIfMissing = true`. Lý do: Tránh làm vỡ kiến trúc Resource Server đã hoạt động trên các service khác.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Không có | Bugfix nội bộ framework và cấu hình service | Không phụ thuộc third-party API |

## 6. Assumptions

- `auth-service` tự chịu trách nhiệm toàn bộ về xác thực (authentication) và phân quyền (authorization) thông qua `SecurityConfig.kt` và `JwtAuthFilter.kt`.
- Các service downstream đóng vai trò là Resource Server nên vẫn cần `DefaultSessionValidationFilter` mặc định bật.

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không |
| Đầy đủ (Completeness) | 24/25 | FR-002: Cần kiểm thử hồi quy các public endpoint khác (/auth/register, /actuator/health) (-1) |
| Nhất quán (Consistency) | 25/25 | Không |
| Kiểm thử được (Testability) | 25/25 | Không |
| **Tổng** | **99/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Completeness | -1 | FR-002 | Cần verify không chỉ endpoint login mà cả register và health | Bổ sung test case curl/test slice cho cả register và actuator |

---

## 8. Issues & Risks

- 🟡 **Cảnh báo Scope cấu hình**: Cần đảm bảo `SecurityProperties` trong `base-security-starter` sử dụng prefix `app.security` thống nhất để không bị mismatch với YAML config.

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 Warning | Mismatch prefix configuration properties giữa base-core và service | FR-001, FR-002 | Sử dụng prefix `app.security.session-validation` rõ ràng và có class properties tương ứng |

## 9. Open Questions

Không có câu hỏi mở.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Framework Starter & Security Infrastructure (`base-security-starter`, `auth-service`)

### 10.2 Flow Type
- Command / Configuration Infrastructure

### 10.3 Candidate Services
- `components/base-core/starters/base-security-starter`: Chứa `SecurityAutoConfiguration.kt`, `SecurityProperties.kt`
- `services/auth-service`: Chứa `src/main/resources/application-security.yml`, `SecurityConfig.kt`

### Detection Evidence
- Keyword: `DefaultSessionValidationFilter` → Module: `base-security-starter` → File: `src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/DefaultSessionValidationFilter.kt`
- Keyword: `SecurityAutoConfiguration` → Module: `base-security-starter` → File: `src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt`
- Keyword: `application-security.yml` → Module: `auth-service` → File: `src/main/resources/application-security.yml`

### 10.4 External Integrations
- Không có (Internal architecture & configuration)

### 10.5 Required Modules
- `components/base-core/starters/base-security-starter`
- `services/auth-service`

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client (Frontend/Curl) | Gửi request `POST /auth/login` không có Bearer token | Web Client |
| 2 | Tomcat Filter Chain | `BaseRequestContextFilter` (-2000) và `HttpLoggingFilter` (-1900) xử lý | Servlet Container |
| 3 | Tomcat Filter Chain | Bỏ qua `DefaultSessionValidationFilter` (đã disabled tại auth-service) | Servlet Container |
| 4 | Spring Security Chain | Chạy qua `FilterChainProxy` (-100), `JwtAuthFilter` cho phép qua do không có token, `AuthorizationFilter` match `.permitAll()` | Spring Security |
| 5 | Controller Layer | `CqrsAuthController.login()` nhận request, dispatch `LoginCommand` | auth-service |
| 6 | Application Layer | `LoginCommandHandler` xác thực password, sinh JWT pair, trả về 200 OK | auth-service |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Bug Investigation | Spec 1 | `com.ntt.basecore.autoconfigure.security.SecurityAutoConfiguration`, `SecurityProperties` | Pending |
| FR-002 | Bug Investigation | Spec 2 | `auth-service/src/main/resources/application-security.yml` | Pending |
| FR-003 | Quality & Stability | Spec 3 | `com.ntt.basecore.autoconfigure.security.SecurityAutoConfiguration` | Pending |
| FR-004 | Bug Investigation | Spec 4 | `com.ntt.authservice.auth.adapter.in.web.CqrsAuthController` | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Lỗi 401 xảy ra ở order `-1700`, hoàn toàn bỏ qua mọi cấu hình `.permitAll()` của Spring Security.
- Đây là bài học điển hình khi thiết kế starter dùng chung cho cả **Identity Provider** lẫn **Resource Server**: cần phân tách rõ tính năng nào dành cho Resource Server để có toggle disable hoặc `@ConditionalOnMissingBean` linh hoạt.

### Related Features / Precedents
- `2026-09-30-fix-security-autoconfig-bean-wiring`: Vừa sửa lỗi wiring bean `DynamicAuthorizationManager` và JPA scan.
- Feature `fix-session-filter-autoconfig-conflict` là bước hoàn thiện tiếp theo để auth-service hoạt động trơn tru toàn diện từ khởi động tới tiếp nhận request đăng nhập từ frontend.

### Suggested Approach
1. Trong `base-security-starter`:
   - Thêm `SessionValidationProperties(var enabled: Boolean = true)` vào `SecurityProperties.kt`.
   - Thêm `@ConditionalOnProperty(prefix = "app.security.session-validation", name = ["enabled"], havingValue = "true", matchIfMissing = true)` trên bean `defaultSessionValidationFilter`.
2. Build và publish `base-security-starter` sang local Maven repository (`publishToMavenLocal`).
3. Trong `auth-service`:
   - Thêm cấu hình trong `application-security.yml`:
     ```yaml
     app:
       security:
         session-validation:
           enabled: false
         public-paths:
           - /auth/**
           - /actuator/**
           - /health/**
     ```
   - Restart hoặc verify login flow.
