<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "Command" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: fix-session-filter-autoconfig-conflict

## 1. Base Security Starter Configuration Enhancement

- [x] **1.1 Add SessionValidationProperties in SecurityProperties**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityProperties.kt` | Action: [MODIFY]
  - FR: FR-001 — Thêm condition toggle cho session validation
  - Details: Thêm data class `SessionValidationProperties(var enabled: Boolean = true)` và property `var sessionValidation: SessionValidationProperties = SessionValidationProperties()` dưới prefix `app.security`.

- [x] **1.2 Add ConditionalOnProperty to defaultSessionValidationFilter**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` | Action: [MODIFY]
  - FR: FR-001, FR-003 — Thêm conditional toggle và duy trì tương thích ngược với `matchIfMissing = true`
  - Details: Gắn annotation `@ConditionalOnProperty(prefix = "app.security.session-validation", name = ["enabled"], havingValue = "true", matchIfMissing = true)` trên bean `defaultSessionValidationFilter`.

- [x] **1.3 Build and publish base-security-starter to Maven Local**
  - File: `components/base-core/starters/base-security-starter` | Action: [BUILD]
  - FR: FR-001, FR-003 — Cập nhật thư viện cục bộ cho các service consumer
  - Command: `./gradlew :starters:base-security-starter:publishToMavenLocal` tại `components/base-core`.

## 2. Auth Service Configuration Update

- [x] **2.1 Disable session validation and configure public-paths in auth-service**
  - File: `services/auth-service/src/main/resources/application-security.yml` | Action: [MODIFY]
  - FR: FR-002 — Vô hiệu hóa Session Validation tại auth-service và phòng thủ chiều sâu
  - Details: Cấu hình `app.security.session-validation.enabled: false` và bổ sung danh sách `app.security.public-paths`: `["/auth/**", "/actuator/**", "/health/**", "/captcha/**", "/.well-known/**", "/unlock/**"]`.

## 3. Verification & Testing

- [x] **3.1 Rebuild and run unit tests**
  - File: `services/auth-service` | Action: [TEST]
  - FR: FR-003, FR-004 — Đảm bảo compile thành công và không bị xung đột bean wiring
  - Command: `./gradlew compileKotlin` tại `services/auth-service`.

- [x] **3.2 Verify login request flow with curl**
  - File: `services/auth-service` | Action: [TEST]
  - FR: FR-004 — Xác thực request `POST /auth/login` không có Bearer token không còn bị trả về 401 ở tầng Servlet filter order -1700
  - Command: `curl -v -X POST http://localhost:8081/auth/login -H "Content-Type: application/json" -d '{"username":"sysadmin","password":"password123"}'`
