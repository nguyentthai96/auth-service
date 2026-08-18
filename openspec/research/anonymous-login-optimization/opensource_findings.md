# Kết quả tìm kiếm Open Source: Anonymous Login Optimization

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login / Guest Session Promotion |
| **Ngày tìm kiếm** | 2025-01-20 |
| **Số dự án tìm thấy** | 7 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Spring Boot, Kotlin, Redis, PostgreSQL, JWT |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"anonymous authentication open source GitHub Spring"` | 5 kết quả relevant | Most are full IAM systems with anonymous as sub-feature |
| 2 | `"guest session promotion library framework Java Kotlin"` | 3 kết quả relevant | Limited standalone libraries — usually embedded in larger frameworks |
| 3 | `"anonymous JWT token management open source"` | 4 kết quả relevant | Several JWT-based auth liumentation | 9 | 15% | 1.35 | Excellent official docs, Admin console, REST API docs |
| Code quality | 8 | 15% | 1.20 | Well-tested, modular architecture with SPIs |
| Community | 10 | 10% | 1.00 | ~25k stars, enterprise adoption (Red Hat) |
| Popularity | 10 | 10% | 1.00 | Industry standard IAM server |
| **Tổng điểm** | | | **7.85/10** | |

#### SuperTokens

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 5 | 20% | 1.00 | Session management focused; no explicit anonymous auth built-in, but session-first architecture enables it with custom recipe |
| Applicability | 4 | 15% | 0.60 | Java core, but designed as standalone server with SDK integration; not embeddable as Spring component |
| Activity | 8 | 15% | 1.20 | Active weekly development |
| Documentation | 8 | 15% | 1.20 | Good docs with recipes and integration guides |
| Code quality | 7 | 15% | 1.05 | Well-structured, decent test coverage |
| Community | 8 | 10% | 0.80 | ~13k stars, growing community |
| Popularity | 7 | 10% | 0.70 | Growing adoption, especially in startup space |
| **Tổng điểm** | | | **6.55/10** | |

#### Spring Security (Anonymous Authentication)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 4 | 20% | 0.80 | Built-in `AnonymousAuthenticationFilter` provides anonymous principal, but no session promotion, no data transfer hooks, no anonymous JWT generation |
| Applicability | 10 | 15% | 1.50 | Already in the project! Native Spring Security integration. Direct applicability |
| Activity | 10 | 15% | 1.50 | Daily commits, core Spring team maintained |
| Documentation | 9 | 15% | 1.35 | Excellent reference docs at docs.spring.io |
| Code quality | 10 | 15% | 1.50 | Industry-leading code quality, comprehensive tests |
| Community | 9 | 10% | 0.90 | ~9k stars, massive Spring ecosystem |
| Popularity | 10 | 10% | 1.00 | De facto standard for JVM security |
| **Tổng điểm** | | | **8.55/10** | |

#### Apereo CAS

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Supports anonymous/guest access with ticket-granting tickets; has concept of "surrogate authentication" (acting as another user); limited session promotion |
| Applicability | 4 | 15% | 0.60 | Java/Spring-based but designed as standalone CAS server; very heavy, enterprise SSO focused |
| Activity | 8 | 15% | 1.20 | Active weekly development, Apereo Foundation maintained |
| Documentation | 7 | 15% | 1.05 | Good docs but complex, steep learning curve |
| Code quality | 7 | 15% | 1.05 | Well-tested but very complex codebase |
| Community | 8 | 10% | 0.80 | ~11k stars, university/enterprise adoption |
| Popularity | 6 | 10% | 0.60 | Niche — mostly higher education and government |
| **Tổng điểm** | | | **6.70/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Spring Security | 4 | 10 | 10 | 9 | 10 | 9 | 10 | **8.55** |
| 2 | Keycloak | 6 | 5 | 9 | 9 | 8 | 10 | 10 | **7.85** |
| 3 | Apereo CAS | 7 | 4 | 8 | 7 | 7 | 8 | 6 | **6.70** |
| 4 | SuperTokens | 5 | 4 | 8 | 8 | 7 | 8 | 7 | **6.55** |

---

## 4. Gap Analysis chi tiết

### Spring Security (Anonymous Authentication) — Gap Analysis

**Overall Score**: 8.55 / 10
**URL**: https://github.com/spring-projects/spring-security

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous principal | ✅ | | `AnonymousAuthenticationFilter` auto-creates anonymous `Authentication` | - |
| Anonymous JWT generation | | ❌ | - | No JWT token generation for anonymous; uses in-memory principal only |
| Session promotion | | ❌ | - | No built-in mechanism to upgrade anonymous → authenticated session |
| Data transfer hooks | | ❌ | - | No callback/event for data migration during auth upgrade |
| Redis session storage | ⚠️ | | Spring Session Redis available | Anonymous-specific TTL not built-in |
| Rate limiting | | ❌ | - | No anonymous creation rate limiting (external library needed) |
| Token lifecycle | | ❌ | - | No anonymous token TTL/rotation management |
| Integration (Spring Boot/Kotlin) | ✅ | | Native integration | - |
| Test coverage | ✅ | | Comprehensive tests | - |

**Verdict**: Tham khảo pattern — Spring Security's `AnonymousAuthenticationFilter` provides the concept but lacks the critical features (JWT generation, session promotion, data transfer). Must build custom implementation.
**Recommendation**: Tham khảo pattern
**Reasoning**: Spring Security's anonymous auth is a server-side concept only (in-memory principal), not a client-facing anonymous token flow. We need to build custom anonymous JWT generation and session promotion on top of the existing Spring Security infrastructure.

### Keycloak — Gap Analysis

**Overall Score**: 7.85 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous/Guest tokens | ⚠️ | | Client credentials grant can act as "anonymous" | Not true anonymous user identity |
| Session promotion | | ❌ | - | No built-in anonymous-to-user session promotion |
| Data transfer hooks | | ❌ | - | No data migration mechanism |
| Scalability | ✅ | | Proven at enterprise scale | - |
| Security | ✅ | | Enterprise-grade security, audit, admin console | - |
| Documentation | ✅ | | Comprehensive admin guide | - |
| Integration (Spring Boot/Kotlin) | ⚠️ | | Spring adapter available | Adds heavy external dependency, separate server |
| Anonymous token lifecycle | | ❌ | - | Must implement custom SPI |

**Verdict**: Tham khảo pattern — Keycloak's architecture is informative (token types, session management) but too heavy as a dependency. The project already has its own auth-service.
**Recommendation**: Tham khảo pattern
**Reasoning**: Keycloak is an external IAM server — integrating it would conflict with the existing auth-service architecture. However, its concepts (service accounts, token exchange, session management) inform the design.

### Apereo CAS — Gap Analysis

**Overall Score**: 6.70 / 10
**URL**: https://github.com/apereo/cas

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous/Guest access | ✅ | | Anonymous access with service tickets | Enterprise SSO focused, overkill for microservice |
| Session promotion | ⚠️ | | "Surrogate authentication" concept | Complex, not direct anonymous→authenticated flow |
| Data transfer hooks | | ❌ | - | Not applicable — SSO ticket-based, no user data storage |
| Scalability | ✅ | | Proven enterprise scale | Heavyweight |
| Integration (Spring Boot) | ⚠️ | | Spring-based but standalone server | Very heavy dependency, 500+ modules |
| Documentation | ✅ | | Comprehensive | Steep learning curve |

**Verdict**: Tham khảo pattern (limited)
**Recommendation**: Tham khảo pattern
**Reasoning**: CAS's anonymous access is SSO-ticket-based, not JWT-based. The "surrogate authentication" concept is interesting but architecturally different from what we need. Too heavy and enterprise-SSO-focused.

### SuperTokens — Gap Analysis

**Overall Score**: 6.55 / 10
**URL**: https://github.com/supertokens/supertokens-core

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Session management | ✅ | | Strong session management with anti-CSRF, rotation | - |
| Anonymous auth | | ❌ | - | No built-in anonymous auth recipe |
| Session promotion | | ❌ | - | No session type upgrade mechanism |
| Data transfer hooks | | ❌ | - | No data migration framework |
| Redis backing | ✅ | | Supports Redis as session store | - |
| Integration (Spring Boot) | ⚠️ | | Java core but designed as SaaS/standalone | External dependency, separate process |

**Verdict**: Tham khảo pattern (session management only)
**Recommendation**: Tham khảo pattern
**Reasoning**: SuperTokens' session management concepts (token rotation, anti-CSRF) are useful references, but it doesn't solve the anonymous auth problem directly. External dependency not justified.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Security | 8.55/10 | Tham khảo pattern | Anonymous principal concept, security filter chain integration |
| 🥈 2 | Keycloak | 7.85/10 | Tham khảo pattern | Token types architecture, session management concepts |
| 🥉 3 | Apereo CAS | 6.70/10 | Tham khảo pattern (limited) | Anonymous access ticket concept |
| 4 | SuperTokens | 6.55/10 | Tham khảo pattern (limited) | Session rotation and anti-CSRF patterns |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Build from scratch** (on top of Spring Security) | No existing open source project provides the complete anonymous-session-promotion pattern needed. All evaluated projects either (a) are standalone IAM servers incompatible with existing architecture, (b) lack session promotion entirely, or (c) provide only partial anonymous auth without data transfer. The project already has a mature auth-service with JwtService, session management, and CQRS patterns — building custom anonymous auth is the most architecturally consistent approach. | Spring Security: highest applicability but missing core features. Keycloak/CAS: too heavy as external dependency. SuperTokens: missing anonymous recipe. Firebase (reference): has anonymous auth + account linking but proprietary. |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)
