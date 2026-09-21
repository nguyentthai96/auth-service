# Proposal: controller-request-context

## Tóm tắt

Chuẩn hóa Controller layer trong auth-service bằng cách:
1. Tạo `RequestContext` (@RequestScope bean) — single source of truth cho request metadata
2. Tạo base controller hierarchy (`BaseController` → `AuthenticatedController` → `AdminController`) — response helpers + user context
3. Consolidate `ClientMetadataFilter` vào `RequestContextFilter` — single filter cho ALL MDC + metadata
4. Tạo DTO→Command extension functions — Kotlin-native mapping convention
5. Migrate `AuditLogService` — inject `RequestContext` cho DDoS/hacking trace

## Phạm vi

| Dimension | Value |
|-----------|-------|
| Type | EXTEND |
| Flow | Command (infrastructure refactoring) |
| Controllers affected | 22 (15 auth + 5 rbac + 1 pbac + 1 excluded) |
| Services affected | 1 (AuditLogService) |
| Files new | 7 |
| Files modified | ~19 |
| Files deleted | 1 (ClientMetadataFilter) |
| API changes | ZERO |

## Kết quả mong đợi

| Metric | Before | After |
|--------|--------|-------|
| `getCurrentUserId()` duplicates | 7 | 0 |
| `extractClientIp()` duplicates | 4 (3 controllers + 1 service) | 0 |
| `messageSource.getMessage()` boilerplate | 18 calls × 3 lines | 18 calls × 1 line |
| `LocaleContextHolder.getLocale()` | ~18 scattered calls | 0 (from RequestContext) |
| MDC management filters | 2 (ClientMetadata + inline) | 1 (RequestContextFilter) |
| Request metadata source | Scattered (controller, filter, service) | Centralized (RequestContext) |

## Phân chia phases

| Phase | Tasks | Risk | Dependencies |
|-------|-------|------|-------------|
| Phase 1: Infrastructure | 5 (RequestContext, Filter, Base classes) | 🟢 Low | None |
| Phase 2: Auth Migration | 11 controllers | 🟡 Medium | Phase 1 |
| Phase 3: RBAC + PBAC + Service | 4 (RBAC, PBAC, AuditLogService) | 🟢 Low | Phase 1 |
| Phase 4: DTO Mappers + Cleanup | 6 (Mappers, test, cleanup) | 🟢 Low | Phase 2, 3 |

## Rủi ro

| Risk | Level | Mitigation |
|------|-------|------------|
| @RequestScope + Virtual Threads | 🟡 | Test with `spring.threads.virtual.enabled=true` |
| AuditLogService in non-web context | 🟡 | `ObjectProvider<RequestContext>` with fallback |
| Filter order change | 🟢 | MDC keys identical, just different source |
| Large number of files modified | 🟡 | Incremental migration (Phase 2 controller-by-controller) |
