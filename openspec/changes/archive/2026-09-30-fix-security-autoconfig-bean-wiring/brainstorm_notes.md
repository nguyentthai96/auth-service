---
type: brainstorm_notes
change: fix-security-autoconfig-bean-wiring
date: 2026-09-30
selected_direction: "C3 — Remove @EnableJpaRepositories from base-core, consumer self-configures"
pre_flow: "MAINTENANCE"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Fix Security AutoConfig Bean Wiring

## Date
2026-09-30

## Context
auth-service khởi động thất bại với lỗi:
```
Parameter 4 of constructor in SecurityConfig required a bean of type
'DynamicAuthorizationManager' that could not be found.
```

## Root Cause Analysis (đã xác nhận qua `--debug`)

### Vấn đề 1: DynamicAuthorizationManager bean không được tạo
- **Debug output**: `@ConditionalOnBean (names: entityManagerFactory) did not find any beans`
- **Lý do**: `@ConditionalOnBean` trên **nested `@Configuration`** class bên trong `@AutoConfiguration` bị evaluate **cùng phase** với outer class — trước khi `entityManagerFactory` bean từ `HibernateJpaAutoConfiguration` được registered.
- **Spring Boot docs confirm**: "We strongly recommend using these conditions only on auto-configuration classes" — NOT on nested `@Configuration` classes.

### Vấn đề 2 (cascading): I18nMessageRepository not found
- Fix vấn đề 1 bằng cách bỏ `@ConditionalOnBean` → `DynamicAuthorizationJpaConfiguration` active
- `@EnableJpaRepositories(basePackages = ["com.ntt.basecore..."])` **override** Spring Boot auto-scan
- auth-service repos (`I18nMessageRepository`, `RoleRepository`, etc.) nằm trong `com.ntt.authservice` → **không được scan**

## Questions Asked & Answers
- Q1: Phạm vi fix? → A: Brainstorm toàn diện (bao gồm I18nMessageRepository + các issues khác)
- Q2: Approach nào cho cascading effect? → A: Approach C — bỏ `@EnableJpaRepositories` khỏi base-core, consumer self-configures
- Q3: Variant nào? → A: C3 — giữ `@EntityScan` (additive), bỏ `@EnableJpaRepositories` (overrides), auth-service tự config

## Approaches Considered

### Approach A: Thêm auth-service packages vào @EnableJpaRepositories trong base-core
- Pros: Simple, 1 file change
- Cons: Couples base-core to auth-service (anti-pattern), mỗi consumer mới phải sửa base-core

### Approach B: Tách DynamicAuthorizationJpaConfiguration thành standalone @AutoConfiguration
- Pros: Đúng Spring Boot pattern, clean separation
- Cons: Phức tạp hơn, thêm file, cần register trong AutoConfiguration.imports

### Approach C: Bỏ @EnableJpaRepositories từ base-core, consumer self-configures ✅
- Pros: Consumer control scan scope, base-core không override, extensible
- Cons: Consumer cần biết thêm base-core repo packages (documentable)

## Selected Direction
**C3**: base-core giữ `@EntityScan` (additive) nhưng bỏ `@EnableJpaRepositories` (overrides). auth-service thêm `@EnableJpaRepositories` include cả hai packages.

## Changes Required

### File 1: `SecurityAutoConfiguration.kt` (base-core)
- Thêm `afterName` cho `HibernateJpaAutoConfiguration`
- Bỏ `@ConditionalOnBean` từ nested class
- Bỏ `@EnableJpaRepositories` từ nested class (overrides consumer repos)
- Bỏ `@EntityScan` từ nested class (cũng overrides consumer entities)

### File 2: `AuthServiceApplication.kt` (auth-service)
- Thêm `@EntityScan` include `com.ntt.authservice` + `com.ntt.basecore.autoconfigure.security.entity`
- Thêm `@EnableJpaRepositories` include `com.ntt.authservice` + `com.ntt.basecore.autoconfigure.security.repository`

## Technical Details

### Both @EnableJpaRepositories AND @EntityScan override auto-scan
- `@EnableJpaRepositories`: Once declared, Spring Boot **disables** auto-configured repository scanning. Only declared packages are scanned.
- `@EntityScan`: Also **overrides** — confirmed by runtime error `Not a managed type: I18nMessageEntity`. NOT additive as initially assumed.
- **Conclusion**: Both annotations in an auto-configuration nested class will break consumer's entity/repo scanning.

### Why @ConditionalOnBean fails on nested @Configuration
- Spring Boot evaluates conditions on nested `@Configuration` classes during the **same phase** as the outer `@AutoConfiguration` — regardless of `afterName` ordering.
- Only `@AutoConfiguration` classes (top-level) respect inter-auto-configuration ordering.
- This is a **documented limitation** of Spring Boot conditional annotations.

## Open Questions for Design Phase
- [RESOLVED] Root cause confirmed via `--debug` output
- [RESOLVED] Cascading effect identified and solution designed
- [RESOLVED] `@EntityScan` cũng override auto-scan (confirmed by `Not a managed type` error at runtime)

## Open Questions for URD Analysis
- Không cần URD — đây là bug fix, không phải feature
