# Kết quả tìm kiếm Open Source: Anonymous Login Optimization

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization |
| **Ngày tìm kiếm** | 2025-01-20 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Spring Boot, Kotlin, Redis, PostgreSQL, JWT |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|--------|
| 1 | `"anonymous authentication session promotion open source GitHub"` | 12 kết quả | Nhiều kết quả liên quan đến Firebase, Keycloak |
| 2 | `"guest session management Spring Boot library"` | 8 kết quả | Ít thư viện chuyên biệt, chủ yếu là patterns |
| 3 | `"anonymous user JWT token management open source"` | 10 kết quả | Tập trung vào JWT libraries, không chuyên anonymous |
| 4 | `"session promotion merge anonymous authenticated Java Kotlin"` | 5 kết quả | Chủ yếu blog posts và reference architectures |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|----------|
| 1 | Keycloak | https://github.com/keycloak/keycloak | 24k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 2 | Spring Session | https://github.com/spring-projects/spring-session | 1.9k+ | Active (monthly) | Apache 2.0 | ✅ Có |
| 3 | Apache Shiro | https://github.com/apache/shiro | 4.3k+ | Active (monthly) | Apache 2.0 | ✅ Có |
| 4 | Spring Security (core) | https://github.com/spring-projects/spring-security | 9k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 5 | Supertokens | https://github.com/supertokens/supertokens-core | 13k+ | Active (weekly) | Apache 2.0 | ❌ Không (Java backend nhưng opinionated, separate service) |
| 6 | Passport.js | https://github.com/jaredhanson/passport | 23k+ | Active | MIT | ❌ Không (Node.js — khác tech stack) |
| 7 | OIDC-Client | https://github.com/authts/oidc-client-ts | 1.5k+ | Active | Apache 2.0 | ❌ Không (client-side only) |
| 8 | Spring Authorization Server | https://github.com/spring-projects/spring-authorization-server | 5k+ | Active | Apache 2.0 | ❌ Không (OAuth2 server, overkill for anonymous) |

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

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|--------|
| Feature completeness | 9 | 20% | 1.80 | Full anonymous/guest user support, session promotion, account linking |
| Applicability | 4 | 15% | 0.60 | Java-based nhưng là separate service (cần deploy riêng, overkill cho use case đơn giản) |
| Activity | 10 | 15% | 1.50 | Weekly commits, very active development |
| Documentation | 9 | 15% | 1.35 | Extensive docs, tutorials, examples |
| Code quality | 9 | 15% | 1.35 | Well-tested, enterprise-grade |
| Community | 10 | 10% | 1.00 | 24k+ stars, huge community |
| Popularity | 10 | 10% | 1.00 | De facto standard for enterprise IAM |
| **Tổng điểm** | | | **8.60/10** | |

#### Spring Session

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|--------|
| Feature completeness | 5 | 20% | 1.00 | Session management tốt nhưng không có anonymous-specific features, không có session promotion built-in |
| Applicability | 9 | 15% | 1.35 | Spring ecosystem native, Redis support built-in |
| Activity | 8 | 15% | 1.20 | Monthly commits, Spring team maintains |
| Documentation | 8 | 15% | 1.20 | Good Spring docs, reference guides |
| Code quality | 9 | 15% | 1.35 | Well-tested, Spring quality standards |
| Community | 8 | 10% | 0.80 | 1.9k stars, widely used |
| Popularity | 8 | 10% | 0.80 | Popular in Spring ecosystem |
| **Tổng điểm** | | | **7.70/10** | |

#### Apache Shiro

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|--------|
| Feature completeness | 6 | 20% | 1.20 | Has guest Subject concept, session management, but limited anonymous-to-auth promotion |
| Applicability | 5 | 15% | 0.75 | Java-based nhưng integration với Spring Boot cần extra config, không Kotlin-native |
| Activity | 5 | 15% | 0.75 | Monthly commits nhưng less active than Spring |
| Documentation | 6 | 15% | 0.90 | Decent docs nhưng hơi outdated |
| Code quality | 7 | 15% | 1.05 | Well-tested, mature codebase |
| Community | 8 | 10% | 0.80 | 4.3k stars, established project |
| Popularity | 6 | 10% | 0.60 | Declining relative to Spring Security |
| **Tổng điểm** | | | **6.05/10** | |

#### Spring Security (core — Anonymous Authentication)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|--------|
| Feature completeness | 4 | 20% | 0.80 | Has AnonymousAuthenticationFilter nhưng rất basic — chỉ inject anonymous principal, không có session data management, không có promotion flow |
| Applicability | 10 | 15% | 1.50 | Already in project (spring-boot-starter-security), native integration |
| Activity | 10 | 15% | 1.50 | Weekly commits, Spring core team |
| Documentation | 8 | 15% | 1.20 | Good docs for anonymous auth filter |
| Code quality | 10 | 15% | 1.50 | Excellent testing, enterprise-grade |
| Community | 9 | 10% | 0.90 | 9k+ stars |
| Popularity | 10 | 10% | 1.00 | Industry standard |
| **Tổng điểm** | | | **8.40/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Keycloak | 9 | 4 | 10 | 9 | 9 | 10 | 10 | **8.60** |
| 2 | Spring Security (Anonymous) | 4 | 10 | 10 | 8 | 10 | 9 | 10 | **8.40** |
| 3 | Spring Session | 5 | 9 | 8 | 8 | 9 | 8 | 8 | **7.70** |
| 4 | Apache Shiro | 6 | 5 | 5 | 6 | 7 | 8 | 6 | **6.05** |

---

## 4. Gap Analysis chi tiết

### Keycloak — Gap Analysis

**Overall Score**: 8.60 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous/Guest User Creation | ✅ | | Full guest user support, auto-create temporary accounts | - |
| Session Promotion | ✅ | | Account linking, identity brokering supports promotion flow | - |
| Temporary Data Storage | | ❌ | - | No built-in cart/preferences merge — only identity merge |
| JWT Token for Anonymous | ✅ | | Can issue tokens for anonymous users | Token claims structure is Keycloak-specific |
| Rate Limiting | ⚠️ | | Basic brute force protection | Not anonymous-session-specific rate limiting |
| Redis Session Storage | ✅ | | Infinispan-based cache (can configure Redis via SPI) | Requires Keycloak's own infra |
| Integration (Spring Boot + Kotlin) | ⚠️ | | Spring adapter available | Requires deploying separate Keycloak server — heavy dependency |
| Lightweight Embedding | | ❌ | - | Cannot embed as library — must run as separate service |

**Verdict**: Tham khảo pattern — quá nặng để adopt cho use case này
**Recommendation**: Tham khảo pattern
**Reasoning**: Keycloak là gold standard cho enterprise IAM nhưng yêu cầu deploy riêng một server. Cho bài toán anonymous login optimization trong một auth-service nhỏ, việc deploy thêm Keycloak là overkill. Tham khảo anonymous user flow và session promotion pattern của Keycloak để build custom.

### Spring Security (Anonymous Authentication) — Gap Analysis

**Overall Score**: 8.40 / 10
**URL**: https://github.com/spring-projects/spring-security

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous Authentication Filter | ✅ | | Built-in `AnonymousAuthenticationFilter`, auto-inject anonymous principal | - |
| Anonymous Token | ✅ | | Generates `AnonymousAuthenticationToken` | Token is in-memory only, not JWT-based |
| Session Promotion | | ❌ | - | No built-in mechanism to promote anonymous → authenticated with data merge |
| Temporary Data Storage | | ❌ | - | No session data management for anonymous users |
| Redis Integration | | ❌ | - | Requires Spring Session addon |
| JWT Anonymous Token | | ❌ | - | `AnonymousAuthenticationToken` is not JWT — need custom implementation |
| Rate Limiting | | ❌ | - | Not included — need separate library |

**Verdict**: Dùng trực tiếp (base) + build custom trên nền
**Recommendation**: Fork + customize — extend Spring Security's anonymous authentication concept, build JWT-based anonymous tokens và session promotion on top
**Reasoning**: Already in project, provides the foundation (SecurityContext, authentication flow, filter chain). Missing pieces (JWT anonymous token, Redis session, promotion flow) need to be custom-built on top of this foundation.

### Spring Session — Gap Analysis

**Overall Score**: 7.70 / 10
**URL**: https://github.com/spring-projects/spring-session

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Session Management | ✅ | | Full session lifecycle management | - |
| Redis Storage | ✅ | | Native Redis session store (`@EnableRedisHttpSession`) | - |
| Session Events | ✅ | | Session created/destroyed/expired events | - |
| Anonymous Session | ⚠️ | | Can store sessions without auth nhưng no anonymous-specific features | No differentiation between anonymous and auth sessions |
| Session Promotion | | ❌ | - | No built-in data merge when changing principal |
| JWT Integration | | ❌ | - | Server-side sessions, not JWT-based |
| Session TTL | ✅ | | Configurable timeout | - |

**Verdict**: Tham khảo pattern — useful cho Redis session storage pattern
**Recommendation**: Tham khảo pattern — borrow Redis session storage approach but implement custom anonymous session logic
**Reasoning**: Spring Session provides excellent Redis integration nhưng designed for server-side sessions. Our project uses stateless JWT. Tham khảo Redis session storage patterns nhưng không dùng trực tiếp vì conflict với JWT-based stateless architecture.

### Apache Shiro — Gap Analysis

**Overall Score**: 6.05 / 10
**URL**: https://github.com/apache/shiro

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Guest Subject | ✅ | | `Subject.isAuthenticated()` returns false for guests | Conceptual only |
| Session Management | ✅ | | Custom session DAO, pluggable session storage | - |
| Session Promotion | ⚠️ | | Can transition Subject from guest to authenticated | No data merge — just re-authenticates |
| Spring Boot Integration | ⚠️ | | Shiro-Spring-Boot starter exists | Less mature than Spring Security integration |
| Redis Session | ⚠️ | | Community Redis session DAO available | Not official |
| JWT Support | | ❌ | - | No native JWT support |

**Verdict**: Không phù hợp
**Recommendation**: Bỏ qua
**Reasoning**: Inferior to Spring Security for Spring Boot projects. Adding Shiro alongside Spring Security would create unnecessary complexity. Guest Subject concept is interesting but too basic.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Keycloak | 8.60/10 | Tham khảo pattern | Enterprise reference architecture, anonymous user flows |
| 🥈 2 | Spring Security (Anonymous) | 8.40/10 | Fork + customize | Foundation layer — extend with custom anonymous JWT |
| 🥉 3 | Spring Session | 7.70/10 | Tham khảo pattern | Redis session storage patterns |
| 4 | Apache Shiro | 6.05/10 | Bỏ qua | N/A |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|--------|
| **Build from scratch** trên nền Spring Security + Redis | Không có open source project nào cung cấp đầy đủ anonymous login + session promotion + JWT + Redis trong một package nhẹ. Keycloak quá nặng (cần deploy riêng). Spring Security cung cấp foundation tốt nhưng missing anonymous JWT + data merge. Spring Session dùng server-side sessions, conflict với JWT stateless architecture. | Keycloak gap: cannot embed. Spring Security gap: no JWT anonymous tokens, no session promotion. Spring Session gap: not JWT-compatible. |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)