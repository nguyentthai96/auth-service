# Handoff Summary: API Response & i18n Standard

## Feature Overview

Chuẩn hóa format API request/response giữa client và server:

1. **Server render message i18n hoàn chỉnh** — client chỉ show, không build string
2. **Dual response format**: Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457)
3. **Client metadata headers**: `Accept-Language`, `X-App-Version`, `X-Client-Platform`
4. **Dual i18n strategy**: Server cho API messages, Frontend cho UI text

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Response format | Dual (ApiResponse + ProblemDetail) | Industry standard, Spring native |
| Error format | RFC 9457 ProblemDetail | Machine-readable, extensible |
| i18n mechanism | Spring `MessageSource` + `.properties` | Built-in, no external deps |
| Locale detection | `Accept-Language` header | HTTP standard (RFC 7231) |
| Frontend UI text | react-i18next (separate) | Already setup, local bundles |
| Client metadata | Custom headers | Standard practice for mobile/web |

## Existing Infrastructure (Reuse-First)

| Component | Reusability |
|-----------|------------|
| `ApiResponse.errorLang()` | ✅ Foundation — wire `MessageSource` as `msgResolver` |
| `ErrorCodeBase.withArgs()` | ✅ Fallback interpolation |
| `ErrorCodeBase.msgCode` | ✅ Direct use as message key |
| `api.ts` `setGlobalHeaders()` | ✅ Inject `Accept-Language` |
| `BaseControllerAdvice` | 🔄 Refactor — add `MessageSource` injection |
| `AuthControllerAdvice` | 🔄 Refactor — resolve `detail` via `MessageSource` |

## Scope Summary

| Layer | New Files | Modified Files | Effort |
|-------|-----------|---------------|--------|
| base-core | 3 (config + messages) | 2 (advice + response) | Medium |
| auth-service | 4 (messages + filter) | 2 (advice + controller) | Medium |
| frontend | 0 | 4 (api, i18n, forms) | Low |
| **Total** | **7** | **8** | **Medium** |

## Risks

| Risk | Mitigation |
|------|-----------|
| `BaseControllerAdvice` change affects all services | Backward compatible — `MessageSource` optional (null-safe) |
| Missing translations | Fallback chain: `messages_vi` → `messages` (English) → `ErrorCodeBase.description` |
| Message key mismatch | Convention: key = `ErrorCodeBase.msgCode` (e.g., `auth.rate_limited`) — already defined |

## Next Steps

```
→ /wf_pre_openspec api-response-i18n-standard
  (Uses this business analysis as URD source)

→ /wf_brainstorm_openspec api-response-i18n-standard
  (Deep thinking with research context)

→ /wf_openspec api-response-i18n-standard
  (Generate implementation artifacts)
```
