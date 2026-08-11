# Validation Report: API Response & i18n Standard

## Review Iteration: 1 (Final)

| Check | Status | Notes |
|-------|--------|-------|
| Source Verification | ✅ PASS | RFC 9457, Spring.io docs, Baeldung — all verified |
| Consistency | ✅ PASS | Business analysis ↔ Technical spec aligned |
| Completeness | ✅ PASS | 4 use cases, 13 business rules, full component list |
| Feasibility | ✅ PASS | `ApiResponse.errorLang()` + `ErrorCodeBase.withArgs()` already exist in base-core |
| Gap Coverage | ✅ PASS | All 6 gaps identified in comparison_analysis covered in tech spec |

## Detail Notes

### Source Verification
- RFC 9457 (Problem Details for HTTP APIs) — IETF standard, verified
- Spring Boot 3.x `MessageSource` + `ProblemDetail` — official docs referenced
- `Accept-Language` + `Content-Language` — RFC 7231 standard
- Codebase scan confirmed: `ApiResponse.errorLang()`, `ErrorCodeBase.withArgs()` exist

### Consistency
- Business rules BR-001→013 all traceable to technical components
- Use cases UC-001→004 all have sequence diagrams in tech spec
- Message bundle key convention matches existing `AuthErrorCode.msgCode` values

### Completeness
- 4 use cases cover all scenarios: error i18n, client headers, success format, frontend UI i18n
- 13 business rules with clear constraints
- Component changes listed for all 3 layers (base-core, auth-service, frontend)

### Feasibility
**Key finding**: base-core đã có ~70% infrastructure cần thiết:
- `ApiResponse.errorLang(err, lang, msgResolver)` — factory method cho i18n error
- `ErrorCodeBase.withArgs(*args)` — string format interpolation
- `ErrorCodeBase.msgCode` — message key convention
- `api.ts` `setGlobalHeaders()` — header injection mechanism

Chỉ cần bổ sung:
- `MessageSource` bean config
- `AcceptHeaderLocaleResolver` config
- Message bundles (`.properties` files)
- Wire `MessageSource` into `ControllerAdvice`

### Gap Coverage
| Gap | Covered By |
|-----|-----------|
| No message bundles | `messages_*.properties` in tech spec |
| No locale resolution | `LocaleConfig.kt` in tech spec |
| `withArgs()` unused for i18n | `MessageSource.getMessage()` replaces direct format |
| `errorLang()` unused | Referenced as foundation for new pattern |
| AuthControllerAdvice inconsistent | Refactor plan in tech spec |
| Frontend hardcodes messages | Simplification plan in tech spec |

## Limitations
- Khmer (`km`) translation cần native speaker review
- `BaseControllerAdvice` refactor ảnh hưởng tất cả services dùng base-core — cần regression testing
