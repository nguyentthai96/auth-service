# Phân tích so sánh: API Response & i18n Standard

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | api-response-i18n-standard |
| **Ngày phân tích** | 2026-08-11 |
| **Recommendation** | **Build from scratch (using Spring native infrastructure)** |
| **Rationale** | Project already has 90%+ implementation in place (DatabaseMessageSource, I18nConfig, AuthControllerAdvice with MessageSource). Spring Boot 3.x native ProblemDetail + MessageSource is the industry standard. No external library adds value — all are subsets of what's already built. |
| **Confidence** | **HIGH** — codebase scan confirms existing implementation, industry patterns validated via RFC 9457 + Spring docs |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Spring Boot 3.x (native) | Framework | Built-in MessageSource + ProblemDetail + AcceptHeaderLocaleResolver | Zero dependency, industry standard, already in stack | Must implement DB source manually | ✅ | 9.65 |
| 2 | error-handling-spring-boot-starter | Open Source | Annotation-driven exception → ProblemDetail mapping with auto MessageSource | Reduces boilerplate, clean API | Convention conflicts with existing ErrorCodeBase, no DB source | ⚠️ | 6.55 |
| 3 | Zalando Problem | Open Source | RFC 7807 Problem abstraction + Jackson serialization | Clean Problem model | No i18n, superseded by Spring Boot 3.x native ProblemDetail | ❌ | 5.85 |
| 4 | Custom Build (current system) | In-house | AuthControllerAdvice + DatabaseMessageSource + Caffeine cache + file bundles | Full control, DB-backed, matches architecture | Maintenance burden | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Spring Native | error-handling-starter | Zalando Problem | Custom Build (current) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| RFC 9457 ProblemDetail | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| MessageSource i18n resolution | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| Accept-Language → Locale | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| Content-Language response header | ✅ (manual) | ❌ | ❌ | ✅ | ⭐ Must |
| Database-backed messages | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Caffeine-cached DB queries | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Custom error code mapping (ErrorCodeBase) | ✅ (manual) | ⚠️ (different convention) | ❌ | ✅ | ⭐ Must |
| Message interpolation ({0}, {1}) | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| Success wrapper (ApiResponse<T>) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Retry-After header | ✅ (manual) | ❌ | ❌ | ✅ | Nice to have |
| Extension properties (ProblemDetail) | ✅ | ✅ | ✅ | ✅ | Nice to have |
| Annotation-driven exception mapping | ❌ | ✅ | ❌ | ❌ | Optional |
| **Coverage** | **7/11** | **6/11** | **3/11** | **11/11** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 9 | 33% (Zalando) - 100% (Custom) |
| Nice to have | 2 | 0% (Zalando) - 100% (Custom) |
| Optional | 1 | 0% (most) - 100% (starter) |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Spring Native | error-handling-starter | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Server-side i18n message rendering | UC-001 | ✅ | ✅ | ✅ | None |
| Database-backed MessageSource with cache | UC-001 | ❌ | ❌ | ✅ | Yes — external solutions lack DB source |
| Dual response format (ApiResponse + ProblemDetail) | UC-003 | ⚠️ (ProblemDetail only) | ⚠️ (ProblemDetail only) | ✅ | Yes — no library supports ApiResponse<T> |
| Accept-Language → locale resolution | UC-001, UC-002 | ✅ | ✅ | ✅ | None |
| Content-Language response header | UC-001 | ✅ (manual) | ❌ | ✅ | Partial — starter lacks this |
| Client metadata headers (X-App-Version) | UC-002 | ✅ (custom filter) | ❌ | ✅ | Yes — no library handles custom headers |
| ErrorCodeBase convention (msgCode) | UC-001 | ✅ (manual mapping) | ⚠️ (different convention) | ✅ | Partial — starter uses FQCN convention |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Error response i18n | AuthControllerAdvice resolves via MessageSource ✅ | Same — already implemented | None | LOW |
| DatabaseMessageSource | Implemented with Caffeine cache ✅ | Same — already implemented | None | LOW |
| File-based message bundles | `auth-messages.properties` (en + vi) ✅ | Add more keys for all 30 error codes | Minor — some keys missing | LOW |
| Success response i18n | `ApiResponse.message` is hardcoded English | `ApiResponse.message` rendered via MessageSource | Gap exists — controllers pass hardcoded strings | MEDIUM |
| Content-Language header | Set in AuthControllerAdvice only | Set on ALL responses (success + error) | Gap — success responses don't have it | MEDIUM |
| Client Accept-Language | Not sent by frontend | Frontend sends `Accept-Language` via ky global headers | Gap — frontend change needed | LOW |
| Client metadata (X-App-Version) | Not sent | Frontend sends `X-App-Version`, `X-Client-Platform` | Gap — frontend + server filter needed | LOW |
| BaseControllerAdvice i18n | No MessageSource injection | Inject MessageSource for base error i18n | Gap — base-core change needed | MEDIUM |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse error-handling-starter | Winner |
|--------|:---:|:---:|:---:|
| Time to market | ~3 developer-days (gaps only) | ~5 developer-days (migration + adaptation) | Custom Build |
| Maintenance burden | Own code — team maintains | External dep — version upgrades | Custom Build |
| Feature coverage | 100% (already 90% done) | 55% (missing DB source, ApiResponse, custom headers) | Custom Build |
| Integration effort | Minimal (extend existing) | High (rewrite exception hierarchy, change conventions) | Custom Build |
| Long-term flexibility | Full control | Limited by library conventions | Custom Build |
| Risk | Low (incremental changes) | Medium (convention conflicts, breaking changes) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Spring Native | error-handling-starter | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 6 | 5 | 10 |
| Integration ease | 25% | 8 | 3 | 10 |
| Maintenance | 20% | 9 | 6 | 7 |
| Community/Support | 15% | 10 | 6 | 5 |
| Learning curve | 10% | 8 | 5 | 9 |
| **Tổng điểm (weighted)** | | **7.90** | **4.85** | **8.60** |

### Reasoning

**Recommended approach**: Build from scratch (continue current implementation using Spring native infrastructure)

**Lý do**:
1. **90%+ already implemented** — DatabaseMessageSource, I18nConfig, AuthControllerAdvice with MessageSource, file bundles (en + vi), Caffeine caching are all in place. Only ~10% gap remains (success message i18n, Content-Language on success, frontend headers).
2. **No external library covers all requirements** — All evaluated libraries lack database-backed MessageSource, Caffeine caching, `ApiResponse<T>` success wrapper, and the custom `ErrorCodeBase` convention. Adopting any would require removing existing features.
3. **Minimal remaining work** — Gaps are small: wire MessageSource into controllers for success messages, add Content-Language to all responses, update frontend to send Accept-Language header.

**Trade-offs chấp nhận**:
- Own maintenance of DatabaseMessageSource — chấp nhận vì complexity is low (57 lines of code) and caching is straightforward
- No auto annotation-driven mapping (like error-handling-starter) — chấp nhận vì explicit `@ExceptionHandler` gives more control and matches CQRS pattern

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| BaseControllerAdvice refactor breaks other services | LOW | HIGH | Nullable MessageSource injection — backward compatible, existing tests pass without MessageSource bean |
| Missing message key at runtime | LOW | LOW | Fallback chain: DB → file → `ErrorCodeBase.description` → default string. Triple fallback ensures no null messages. |
| Frontend Accept-Language not sent correctly | LOW | LOW | ky's `beforeRequest` hook is well-tested. Fallback to `en` locale if header missing. |
| Khmer translation quality | MEDIUM | LOW | Structure in place, content can be added later by native speaker. Fallback to English. |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build (continue current) | 3 | LOW | LOW |
| error-handling-starter (migration) | 5 | MEDIUM | MEDIUM |
| Spring Native only (remove DB source) | 2 | LOW | MEDIUM (lose runtime message management) |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
