# Pre-OpenSpec: fix-security-autoconfig-bean-wiring

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: User Idea (runtime error analysis)
> **Classification Evidence**: `SecurityAutoConfiguration` → `base-security-starter` → `SecurityAutoConfiguration.kt`
> **Archive**: N/A
> **Quality Score**: 90/100

## 📋 Feature Summary

Fix chuỗi lỗi bean wiring khi auth-service khởi động: `DynamicAuthorizationManager` bean không được tạo do `@ConditionalOnBean` trên nested `@Configuration` class bị evaluate sai phase, kéo theo `@EntityScan` và `@EnableJpaRepositories` trong base-core override auto-scan scope của consumer service.

| Metric | Giá trị |
|--------|---------|
| Số FR | 4 (URD: 0, Enriched: 4) |
| Issues | 0 (🔴: 0, 🟡: 0) |
| Open Questions | 0 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **Developer**: Người phát triển sử dụng `base-security-starter` trong Spring Boot services
- **Spring Boot AutoConfiguration**: Hệ thống tự động cấu hình beans dựa trên classpath và conditions

## 2. Functional Requirements

### FR-001: Sửa @ConditionalOnBean trên nested @Configuration [ENRICHED]
- **Actor**: Spring Boot AutoConfiguration
- **Action**: Hệ thống phải tạo `DynamicAuthorizationManager` bean khi JPA classpath available, KHÔNG phụ thuộc vào `@ConditionalOnBean` timing
- **Validation**: `DynamicAuthorizationManager` bean available cho injection

### FR-002: Sửa @EnableJpaRepositories override auto-scan [ENRICHED]
- **Actor**: Spring Boot AutoConfiguration
- **Action**: Hệ thống phải scan JPA repositories từ cả base-core và consumer service packages
- **Validation**: `EndpointSecurityRuleRepository` + `I18nMessageRepository` + các repos khác đều available

### FR-003: Sửa @EntityScan override auto-scan [ENRICHED]
- **Actor**: Spring Boot AutoConfiguration
- **Action**: Hệ thống phải scan JPA entities từ cả base-core và consumer service packages
- **Validation**: `EndpointSecurityRuleEntity` + `I18nMessageEntity` + các entities khác đều managed

### FR-004: Đảm bảo auto-configuration ordering đúng [ENRICHED]
- **Actor**: Spring Boot AutoConfiguration
- **Action**: `SecurityAutoConfiguration` phải chạy SAU `HibernateJpaAutoConfiguration` để JPA infrastructure sẵn sàng
- **Validation**: `afterName` includes `HibernateJpaAutoConfiguration`

## 3. Non-functional Requirements

- Backward compatible — consumer services hiện tại cần thêm `@EntityScan` + `@EnableJpaRepositories`
- Không thay đổi business logic, chỉ sửa bean wiring infrastructure

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

Tất cả 4 FRs đều [ENRICHED] — phát hiện từ runtime error analysis, không có URD gốc.

### Enriched FRs

- FR-001: Phát hiện từ `--debug` output — `@ConditionalOnBean` evaluate sai phase
- FR-002: Phát hiện sau khi fix FR-001 — `@EnableJpaRepositories` override consumer scan
- FR-003: Phát hiện sau khi fix FR-002 — `@EntityScan` cũng override (không additive)
- FR-004: Cần `afterName` để đảm bảo ordering

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Maven Local | Publish base-core artifacts | Rebuild + publishToMavenLocal |

## 6. Assumptions

- Các consumer services khác (nếu có) cũng cần thêm `@EntityScan` + `@EnableJpaRepositories` tương tự auth-service
- Spring Boot 4.x behavior cho `@EntityScan` / `@EnableJpaRepositories` override là consistent

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-001: root cause phức tạp, cần Spring Boot internal knowledge |
| Đầy đủ (Completeness) | 23/25 | Cascading effects phát hiện iteratively |
| Nhất quán (Consistency) | 22/25 | @EntityScan ban đầu giả định additive — sai |
| Kiểm thử được (Testability) | 22/25 | Chỉ test được bằng bootRun, khó unit test |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -2 | FR-001 | Root cause cần Spring Boot internal knowledge | Document trong brainstorm notes |
| 2 | Completeness | -2 | FR-003 | Phát hiện cascading effect iteratively | Cần regression test suite |
| 3 | Consistency | -3 | FR-003 | @EntityScan giả định sai ban đầu | Verified via runtime error |
| 4 | Testability | -3 | ALL | Chỉ bootRun test, không unit testable | Consider integration test |

---

## 8. Issues & Risks

> Không phát hiện vấn đề (đã fix + verify thành công).

## 9. Open Questions

> Không có câu hỏi mở.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Security Auto-Configuration — Bean wiring infrastructure

### 10.2 Flow Type
Command (configuration change)

### 10.3 Candidate Services
- **base-security-starter** (base-core): `SecurityAutoConfiguration.kt` — source of the bug
- **auth-service**: `AuthServiceApplication.kt` — consumer needs explicit scan config

### Detection Evidence
- Error: `UnsatisfiedDependencyException` → Class: `SecurityConfig` → Bean: `DynamicAuthorizationManager`
- Debug: `@ConditionalOnBean did not find any beans` → File: `SecurityAutoConfiguration.kt`
- Cascading: `Not a managed type: I18nMessageEntity` → `@EntityScan` override

### 10.4 External Integrations
Maven Local (publishToMavenLocal for base-core changes)

### 10.5 Required Modules
- `base-security-starter` (base-core component)
- `auth-service` (consumer service)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | bootRun auth-service | Spring Boot starts context refresh |
| 2 | Spring | Evaluate SecurityAutoConfiguration conditions | Check @ConditionalOnClass, @ConditionalOnProperty |
| 3 | Spring | Evaluate DynamicAuthorizationJpaConfiguration | Create DAM bean (was failing before fix) |
| 4 | Spring | Scan JPA entities + repositories | Use @EntityScan + @EnableJpaRepositories from AuthServiceApplication |
| 5 | Spring | Create SecurityConfig | Inject DynamicAuthorizationManager (now available) |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Runtime error | brainstorm_notes §Root Cause | `SecurityAutoConfiguration.DynamicAuthorizationJpaConfiguration` | ✅ Fixed |
| FR-002 | Cascading error | brainstorm_notes §Cascading | `SecurityAutoConfiguration.DynamicAuthorizationJpaConfiguration` | ✅ Fixed |
| FR-003 | Cascading error | brainstorm_notes §Cascading | `SecurityAutoConfiguration.DynamicAuthorizationJpaConfiguration` | ✅ Fixed |
| FR-004 | Root cause | brainstorm_notes §Root Cause | `SecurityAutoConfiguration` | ✅ Fixed |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
Bug fix này thuộc dạng **infrastructure wiring** — không ảnh hưởng business logic nhưng block toàn bộ service startup. Root cause phức tạp vì liên quan đến Spring Boot internal condition evaluation order. Cascading effects (3 lỗi liên tiếp) cần phát hiện iteratively qua debug → fix → test cycle.

### Related Features / Precedents
Không có archive tương tự. Đây là lần đầu gặp pattern `@ConditionalOnBean` trên nested `@Configuration` trong project.

### Integration Notes
- base-core publish qua Maven Local — consumer phải clean Gradle cache sau khi rebuild
- Các consumer khác của `base-security-starter` cần thêm `@EntityScan` + `@EnableJpaRepositories`

### Suggested Approach
Đã implement theo Approach C3 (brainstorm_notes):
1. base-core: Bỏ `@ConditionalOnBean`, `@EnableJpaRepositories`, `@EntityScan` từ nested class + thêm `afterName`
2. auth-service: Thêm `@EntityScan` + `@EnableJpaRepositories` include cả hai packages

### Context from Confluence Images
N/A
