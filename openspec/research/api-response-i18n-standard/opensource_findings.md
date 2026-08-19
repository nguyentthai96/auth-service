# Kết quả tìm kiếm Open Source: API Response & i18n Standard

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | api-response-i18n-standard |
| **Ngày tìm kiếm** | 2026-08-11 |
| **Số dự án tìm thấy** | 6 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot 3.x / PostgreSQL |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"spring boot error handling i18n open source GitHub"` | 4 kết quả liên quan | Found wimdeblauwe/error-handling-spring-boot-starter |
| 2 | `"API response i18n library spring framework"` | 3 kết quả liên quan | Found spring-projects/spring-framework MessageSource |
| 3 | `"RFC 9457 ProblemDetail spring implementation"` | 3 kết quả liên quan | Spring native support since Boot 3.0 |
| 4 | `"database backed message source spring i18n"` | 2 kết quả liên quan | Various custom implementations |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | error-handling-spring-boot-starter | https://github.com/wimdeblauwe/error-handling-spring-boot-starter | ~800 | 2024-11 | Apache 2.0 | ✅ Có |
| 2 | Spring Framework (MessageSource) | https://github.com/spring-projects/spring-framework | ~57k | Active | Apache 2.0 | ✅ Có |
| 3 | Zalando Problem | https://github.com/zalando/problem | ~900 | 2023-09 | MIT | ✅ Có |
| 4 | Zalando Problem Spring Web | https://github.com/zalando/problem-spring-web | ~1k | 2023-10 | MIT | ✅ Có |
| 5 | MessageSource DB examples | Various blog repos | <50 | Varies | Varies | ❌ Không (quá nhỏ, demo code) |
| 6 | spring-boot-starter-i18n | Various GitHub repos | <100 | Varies | Varies | ❌ Không (không maintained) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### error-handling-spring-boot-starter (wimdeblauwe)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Auto-maps exceptions to RFC 7807 ProblemDetail, i18n via MessageSource, customizable error codes |
| Applicability | 8 | 15% | 1.20 | Spring Boot 3.x native, Kotlin compatible, same tech stack |
| Activity | 5 | 15% | 0.75 | Last commit Nov 2024 — moderately active, releases periodic |
| Documentation | 7 | 15% | 1.05 | Good README + docs site, code examples |
| Code quality | 7 | 15% | 1.05 | Well-tested, clean Java code |
| Community | 6 | 10% | 0.60 | ~800 stars — growing niche |
| Popularity | 5 | 10% | 0.50 | Used in medium-sized projects, not mainstream |
| **Tổng điểm** | | | **6.55/10** | |

#### Spring Framework MessageSource (native)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full i18n: MessageSource, LocaleResolver, ProblemDetail, resource bundles, DB extensible via AbstractMessageSource |
| Applicability | 10 | 15% | 1.50 | Already in our stack — zero integration cost |
| Activity | 10 | 15% | 1.50 | Active weekly development by VMware/Broadcom |
| Documentation | 9 | 15% | 1.35 | Extensive official docs, Baeldung guides, SO answers |
| Code quality | 10 | 15% | 1.50 | Battle-tested, millions of production deployments |
| Community | 10 | 10% | 1.00 | 57k+ stars — de facto standard |
| Popularity | 10 | 10% | 1.00 | Industry standard framework |
| **Tổng điểm** | | | **9.65/10** | |

#### Zalando Problem

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 6 | 20% | 1.20 | RFC 7807 Problem abstraction, serialization, exception mapping — but NO built-in i18n |
| Applicability | 5 | 15% | 0.75 | Java library, Spring Boot 3.x has native ProblemDetail now — largely redundant |
| Activity | 3 | 15% | 0.45 | Last commit Sept 2023 — maintenance mode |
| Documentation | 7 | 15% | 1.05 | Good README with examples |
| Code quality | 8 | 15% | 1.20 | Well-tested, clean code |
| Community | 7 | 10% | 0.70 | ~900 stars |
| Popularity | 5 | 10% | 0.50 | Was popular pre-Spring Boot 3.0, declining |
| **Tổng điểm** | | | **5.85/10** | |

#### Zalando Problem Spring Web

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Spring integration for Zalando Problem, ControllerAdvice auto-config, exception → Problem mapping |
| Applicability | 4 | 15% | 0.60 | Pre-Spring Boot 3.0 design — Spring Boot 3.x has native `ResponseEntityExceptionHandler` with ProblemDetail now |
| Activity | 3 | 15% | 0.45 | Last commit Oct 2023 — near EOL |
| Documentation | 6 | 15% | 0.90 | README + wiki |
| Code quality | 7 | 15% | 1.05 | Good test coverage |
| Community | 7 | 10% | 0.70 | ~1k stars |
| Popularity | 4 | 10% | 0.40 | Declining — Spring Boot 3.x native ProblemDetail replaces it |
| **Tổng điểm** | | | **5.50/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Spring Framework (native) | 9 | 10 | 10 | 9 | 10 | 10 | 10 | **9.65** |
| 2 | error-handling-spring-boot-starter | 7 | 8 | 5 | 7 | 7 | 6 | 5 | **6.55** |
| 3 | Zalando Problem | 6 | 5 | 3 | 7 | 8 | 7 | 5 | **5.85** |
| 4 | Zalando Problem Spring Web | 7 | 4 | 3 | 6 | 7 | 7 | 4 | **5.50** |

---

## 4. Gap Analysis chi tiết

### Spring Framework MessageSource — Gap Analysis

**Overall Score**: 9.65 / 10
**URL**: https://github.com/spring-projects/spring-framework

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| MessageSource interface | ✅ | | Core abstraction for i18n message resolution | - |
| AcceptHeaderLocaleResolver | ✅ | | Native locale resolution from HTTP headers | - |
| ProblemDetail (RFC 9457) | ✅ | | Built-in since Boot 3.0 with `ResponseEntityExceptionHandler` | - |
| Database-backed MessageSource | | ❌ | - | Must implement `AbstractMessageSource` manually |
| Message interpolation | ✅ | | `MessageFormat {0}, {1}` standard | - |
| Content-Language header | ✅ | | Available via `LocaleContextHolder` | Must set manually on response |
| Error code mapping | | ❌ | - | No convention for error code → message key — must define |
| Cache for DB messages | | ❌ | - | Must implement caching layer (Caffeine/Redis) |

**Verdict**: Dùng trực tiếp — đã là foundation của project
**Recommendation**: Dùng trực tiếp
**Reasoning**: Spring Framework's MessageSource is already the project's core i18n mechanism. All other libraries are built on top of it. The gaps (DB-backed source, caching) are already implemented in auth-service as `DatabaseMessageSource`.

### error-handling-spring-boot-starter — Gap Analysis

**Overall Score**: 6.55 / 10
**URL**: https://github.com/wimdeblauwe/error-handling-spring-boot-starter

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Auto exception → ProblemDetail | ✅ | | Zero-config exception mapping via annotations | - |
| i18n message resolution | ✅ | | Auto MessageSource lookup by exception class name | Convention may conflict with existing `msgCode` |
| Custom error codes | ✅ | | `@ResponseErrorCode` annotation | Different convention than our `ErrorCodeBase` |
| Extension properties | ✅ | | `@ResponseErrorProperty` for custom fields | - |
| Database-backed messages | | ❌ | - | File-based only |
| Dual format (ApiResponse + ProblemDetail) | | ❌ | - | ProblemDetail only — no success wrapper |
| Clean Architecture support | ⚠️ | | Works but opinionated — annotation-driven | May conflict with CQRS handler pattern |

**Verdict**: Tham khảo pattern
**Recommendation**: Tham khảo pattern — không adopt
**Reasoning**: Library provides good patterns for exception-to-ProblemDetail mapping, but our project already has a more sophisticated setup (custom `AuthControllerAdvice` with `extractMessageArgs()`, `DatabaseMessageSource`, Caffeine caching). Adopting would require rewriting existing exception hierarchy and losing DB-backed i18n.

### Zalando Problem — Gap Analysis

**Overall Score**: 5.85 / 10
**URL**: https://github.com/zalando/problem

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| RFC 7807 Problem abstraction | ✅ | | Clean immutable Problem class | - |
| Serialization (Jackson) | ✅ | | Auto Jackson module for Problem JSON | Spring Boot 3.x has this natively |
| i18n support | | ❌ | - | No i18n — messages are hardcoded strings |
| Spring Boot 3.x compatibility | ⚠️ | | Works but redundant | Spring's native `ProblemDetail` replaces this |
| Active maintenance | | ❌ | - | Maintenance mode since 2023 |

**Verdict**: Bỏ qua
**Recommendation**: Bỏ qua
**Reasoning**: Spring Boot 3.x's native `ProblemDetail` provides everything Zalando Problem offered, plus better integration. Project already uses Spring's native ProblemDetail.

### Zalando Problem Spring Web — Gap Analysis

**Overall Score**: 5.50 / 10
**URL**: https://github.com/zalando/problem-spring-web

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Spring ControllerAdvice integration | ✅ | | Auto exception handling traits | Spring Boot 3.x `ResponseEntityExceptionHandler` replaces |
| Stacktrace control | ✅ | | Configurable stacktrace inclusion | - |
| i18n support | | ❌ | - | No MessageSource integration |
| Spring Boot 3.x support | ⚠️ | | Partial — largely superseded | Near EOL |

**Verdict**: Bỏ qua
**Recommendation**: Bỏ qua
**Reasoning**: Superseded by Spring Boot 3.x native ProblemDetail support. Adding this would introduce unnecessary dependency.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Framework (native) | 9.65/10 | Dùng trực tiếp | Core i18n infrastructure — MessageSource, LocaleResolver, ProblemDetail |
| 🥈 2 | error-handling-spring-boot-starter | 6.55/10 | Tham khảo pattern | Pattern reference for annotation-driven exception-to-error mapping |
| 🥉 3 | Zalando Problem | 5.85/10 | Bỏ qua | N/A — superseded by Spring Boot 3.x native ProblemDetail |
| 4 | Zalando Problem Spring Web | 5.50/10 | Bỏ qua | N/A — superseded by Spring Boot 3.x native ProblemDetail |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| Build from scratch (using Spring native) | Spring Framework's MessageSource + ProblemDetail already provides 100% of required infrastructure. Project already has sophisticated implementation (DatabaseMessageSource, Caffeine cache, composite resolution chain). No external library adds value. | Codebase scan: `I18nConfig.kt`, `DatabaseMessageSource.kt`, `GlobalExceptionHandler.kt` already implement the pattern. |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-11
> **Next step**: Comparison Analysis (comparison_analysis.md)
