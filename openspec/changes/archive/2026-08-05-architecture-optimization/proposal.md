# Proposal: Architecture Optimization — auth-service

## Tổng quan

Chuẩn hoá kiến trúc `auth-service` theo Clean/Hexagonal Architecture, nâng adoption base-core từ 15% lên >80%, tối ưu cho quy mô 1B users / 100M TPS. Đây là **EXTEND change** (mở rộng adoption infrastructure đã có, KHÔNG phải NEWBUILD).

## Vấn đề hiện tại

| # | Vấn đề | Evidence | Ảnh hưởng |
|---|--------|----------|-----------|
| 1 | God-class `AuthService` (307 lines, 11 deps) | [AuthService.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | Blast radius lớn khi sửa |
| 2 | N+1 queries trong `RbacEngine` | [RbacEngine.kt:L66-71](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt#L66-L71) | Performance bottleneck |
| 3 | Không có domain layer | Toàn bộ logic dùng JPA entities trực tiếp | Coupling framework |
| 4 | base-core chỉ dùng 2/13 features (15%) | `CommandBus`, `MapStruct`, `BaseControllerAdvice` đều có nhưng không adopt | Duplicate effort |
| 5 | `RestTemplate` inline | [SsoAdapter.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt), [CaptchaVerifier.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt) | Không type-safe, hard to test |
| 6 | `AuthException` không extend `BusinessException` | [AuthExceptions.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt) | Không tương thích base-core |
| 7 | Unbounded thread pool | [PolicyEvaluator.kt:L29](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/pbac/application/PolicyEvaluator.kt#L29) | Memory leak risk |

## Giải pháp đề xuất

### Approach: Pragmatic Hybrid (Bridge Pattern)

**Lý do chọn** (từ brainstorm):
- Zero breaking changes cho API consumers
- base-core compatible — `AuthException extends BusinessException`
- Phase 1 có ZERO blast radius (chỉ thêm files mới)
- Phase 2 dùng feature flag cho gradual rollout

### 5 Phases

| Phase | Nội dung | Thời gian | Risk |
|:-----:|----------|:---------:|:----:|
| **P1** | Domain Layer + Ports + Mappers + Exception Bridge | 1 tuần | 🟢 Low |
| **P2** | CQRS Handlers + Controller Rewire | 1.5 tuần | 🟡 Medium |
| **P3** | gRPC + @HttpExchange + Shared Modules | 1.5 tuần | 🟢 Low |
| **P4** | Performance (N+1 fix, Cache, Virtual Threads) | 1 tuần | 🟢 Low |
| **P5** | ArchUnit + Unit + Integration Tests | 1.5 tuần | 🟢 None |

### Phạm vi

| Scope | Chi tiết |
|-------|----------|
| **Primary** | auth-service (4 bounded contexts: auth, rbac, pbac, shared) |
| **Secondary** | base-core/base-model (thêm `VersionedAuditableEntity`) |
| **New Modules** | `services/shared/grpc-proto/`, `domain-common/`, `security-common/` |
| **Out of Scope** | account-service, system-admin-service migration (riêng biệt) |

## Impact Summary

- **Files modified**: ~10 (controllers, build configs, exceptions)
- **Files added**: ~55 (domain models, handlers, adapters, tests, proto)
- **Files deleted**: 2 (AuthService.kt, RbacEngine.kt — replaced by handlers)
- **API breaking changes**: 0
- **FRs covered**: 25 (20 URD + 5 Enriched)

## Constraints

- Spring Boot 4.1.0 / Kotlin 2.4.10 / JDK 25 LTS
- base-core platform BOM v4.1.0
- `@Version` chỉ cho VersionedAuditableEntity (opt-in)
- Proto files ở `services/shared/grpc-proto/` (KHÔNG ở base-core)
- ProblemDetail (RFC 7807) — giữ nguyên, KHÔNG chuyển sang ApiResponse

## Success Criteria

| Metric | Before | After |
|--------|:------:|:-----:|
| base-core adoption | 15% (2/13) | >80% (10/13) |
| AuthService LOC | 307 | 0 (replaced by 9 handlers) |
| Handler avg LOC | N/A | ≤50 |
| Handler avg deps | 11 (god-class) | ≤4 |
| N+1 queries | 3 per permission check | 1 batch JOIN |
| Domain purity | 0% | 100% (no framework imports) |
| Test coverage | ~0% | >80% (domain + handlers) |
