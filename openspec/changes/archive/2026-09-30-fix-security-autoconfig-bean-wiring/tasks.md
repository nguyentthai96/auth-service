<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: fix-security-autoconfig-bean-wiring

> Profile: MAINTENANCE | Command | Approach C3
> Status: ✅ IMPLEMENTED + VERIFIED

---

- [x] **Task 1: Thêm afterName cho HibernateJpaAutoConfiguration**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` | Action: [MODIFY]
  - FR: FR-004 — Đảm bảo auto-configuration ordering
  - Pattern: `@AutoConfiguration(afterName = [...])`
  - Dependencies: N/A

- [x] **Task 2: Bỏ @ConditionalOnBean từ DynamicAuthorizationJpaConfiguration**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` | Action: [MODIFY]
  - FR: FR-001 — Sửa condition evaluation phase
  - Pattern: Giữ `@ConditionalOnClass` (classpath check), bỏ `@ConditionalOnBean` (runtime check unreliable)
  - Dependencies: Task 1

- [x] **Task 3: Bỏ @EnableJpaRepositories từ DynamicAuthorizationJpaConfiguration**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` | Action: [MODIFY]
  - FR: FR-002 — Sửa repository scan override
  - Pattern: Consumer self-declares `@EnableJpaRepositories`
  - Dependencies: Task 1

- [x] **Task 4: Bỏ @EntityScan từ DynamicAuthorizationJpaConfiguration**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` | Action: [MODIFY]
  - FR: FR-003 — Sửa entity scan override
  - Pattern: Consumer self-declares `@EntityScan`
  - Dependencies: Task 1

- [x] **Task 5: Thêm @EntityScan + @EnableJpaRepositories vào AuthServiceApplication**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/AuthServiceApplication.kt` | Action: [MODIFY]
  - FR: FR-002, FR-003 — Consumer tự control scan scope
  - Pattern: Include cả `com.ntt.authservice` + `com.ntt.basecore.autoconfigure.security.*` packages
  - Dependencies: Task 3, Task 4

- [x] **Task 6: Rebuild base-security-starter + publishToMavenLocal**
  - File: `components/base-core/` | Action: Build
  - FR: ALL — Cần publish để auth-service dùng
  - Pattern: `./gradlew :starters:base-security-starter:build -x test && ./gradlew :starters:base-security-starter:publishToMavenLocal`
  - Dependencies: Task 1-4

- [x] **Task 7: Verify auth-service startup**
  - File: N/A | Action: Test
  - FR: ALL — Verification
  - Pattern: `./gradlew clean bootRun` — expect `Started AuthServiceApplicationKt`
  - Dependencies: Task 5, Task 6

---

## Verification Result

```
Started AuthServiceApplicationKt in 18.561 seconds (process running for 19.234)
```

✅ Tất cả 7 tasks hoàn thành. auth-service khởi động thành công.
