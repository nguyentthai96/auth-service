# Impact Analysis

## 1. Core Files Affected
- `base-testing-starter/build.gradle.kts`: [MODIFY] Thêm dependency `datasource-proxy`, K6 plugins.
- `base-testing-starter/src/main/kotlin/com/ntt/basecore/testing/db/AssertQueryCount.kt`: [NEW]
- `auth-service/tests/load/auth_flow.js`: [NEW]
- `auth-service/tests/load/profile_flow.js`: [NEW]
- `auth-service/build.gradle.kts`: [MODIFY] Thêm custom gradle task cho K6.
- `auth-service/src/test/kotlin/com/ntt/authservice/auth/application/CacheEncryptionIntegrationTest.kt`: [NEW]

## 2. Blast Radius
- 🟢 **Low**: Tính năng thêm file test nội bộ và custom config chạy test, hoàn toàn cô lập khỏi môi trường production.
- Thay đổi `base-testing-starter` chỉ ảnh hưởng scope `testImplementation` trong các service tiêu thụ.

## 3. Call Tree & Flow
- `Test` -> `@SpringBootTest` -> `CacheEncryptionIntegrationTest` -> `StringRedisTemplate` -> Assert Data Format.
- `Gradle` -> `k6Run` task -> `docker run loadimpact/k6` -> HTTP Requests -> `auth-service`.

## 4. Reuse Map
- Kế thừa lại module `Testcontainers` đã cấu hình sẵn PostgreSQL và Redis từ `base-testing-starter`.

## 5. Context Snapshot
- `AuthServiceApplication` đã cấu hình `@EnableCaching` và tích hợp thành công thuật toán AES-GCM, sẵn sàng cho việc kiểm thử tính an toàn.
