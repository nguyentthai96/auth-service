# Kết quả tìm kiếm Open Source: JWT Token Validation

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày tìm kiếm** | 2025-07-11 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 5 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot 3.x / jjwt / Redis / PostgreSQL |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"JWT validation Java Kotlin open source library GitHub"` | 8 kết quả | Found major JWT libraries |
| 2 | `"Spring Security JWT validation resource server"` | 5 kết quả | Framework-level support |
| 3 | `"Keycloak JWT validation token introspection"` | 4 kết quả | Full IAM platform |
| 4 | `"auth0 java-jwt library RS256 validation"` | 3 kết quả | Auth0's library |
| 5 | `"fusionauth JWT token management open source"` | 3 kết quả | Alternative IAM |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | jjwt (JSON Web Token for Java) | https://github.com/jwtk/jjwt | ~10.3K | Active (monthly) | Apache 2.0 | ✅ Có |
| 2 | nimbus-jose-jwt | https://connect2id.com/products/nimbus-jose-jwt | ~2K (Maven Central) | Active (monthly) | Apache 2.0 | ✅ Có |
| 3 | java-jwt (Auth0) | https://github.com/auth0/java-jwt | ~5.6K | Active (quarterly) | MIT | ✅ Có |
| 4 | Spring Security OAuth2 Resource Server | https://github.com/spring-projects/spring-security | ~9.2K | Active (weekly) | Apache 2.0 | ✅ Có |
| 5 | Keycloak | https://github.com/keycloak/keycloak | ~24K | Active (daily) | Apache 2.0 | ✅ Có |
| 6 | FusionAuth | https://github.com/FusionAuth/fusionauth-jwt | ~0.6K | Active (quarterly) | Apache 2.0 | ❌ Không (niche, small community) |
| 7 | Vert.x JWT | https://github.com/eclipse-vertx/vertx-auth | ~0.3K | Active | Apache 2.0/EPL 2.0 | ❌ Không (Vert.x-specific) |
| 8 | Pac4j | https://github.com/pac4j/pac4j | ~2.4K | Active | Apache 2.0 | ❌ Không (multi-protocol, overhead) |

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

#### jjwt (JSON Web Token for Java)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full JWS/JWE support, all algorithms, claims validation, JWKS |
| Applicability | 10 | 15% | 1.50 | Already used in project (io.jsonwebtoken), Kotlin-friendly fluent API |
| Activity | 8 | 15% | 1.20 | Regular releases, responsive maintainers |
| Documentation | 8 | 15% | 1.20 | Comprehensive README, wiki, migration guides |
| Code quality | 9 | 15% | 1.35 | Extensive test suite, clean modular architecture (api/impl/jackson) |
| Community | 9 | 10% | 0.90 | ~10.3K stars, widely used in Spring ecosystem |
| Popularity | 9 | 10% | 0.90 | De facto standard for Java JWT |
| **Tổng điểm** | | | **8.85/10** | |

#### nimbus-jose-jwt

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Most complete JOSE implementation — JWS, JWE, JWK, JWT, JWKS all RFC-compliant |
| Applicability | 7 | 15% | 1.05 | Compatible but would require migration from jjwt; used by Spring Security internally |
| Activity | 9 | 15% | 1.35 | Very active, maintained by Connect2id |
| Documentation | 7 | 15% | 1.05 | Good Javadoc, cookbook examples, but less tutorial-style than jjwt |
| Code quality | 9 | 15% | 1.35 | Excellent test coverage, strict RFC compliance |
| Community | 7 | 10% | 0.70 | Smaller GitHub presence but heavily used via Spring Security |
| Popularity | 8 | 10% | 0.80 | Default in Spring Security OAuth2 Resource Server |
| **Tổng điểm** | | | **8.30/10** | |

#### java-jwt (Auth0)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | JWS only (no JWE), basic validation, RS256/HS256/ES256 |
| Applicability | 6 | 15% | 0.90 | Different API style from jjwt; migration effort needed |
| Activity | 7 | 15% | 1.05 | Maintained by Auth0, quarterly releases |
| Documentation | 8 | 15% | 1.20 | Good README, Auth0 ecosystem docs |
| Code quality | 7 | 15% | 1.05 | Adequate tests, simpler codebase |
| Community | 8 | 10% | 0.80 | ~5.6K stars, Auth0 brand backing |
| Popularity | 7 | 10% | 0.70 | Popular in Auth0 ecosystem, less so standalone |
| **Tổng điểm** | | | **7.10/10** | |

#### Spring Security OAuth2 Resource Server

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | JWKS auto-fetching, issuer validation, audience check, NimbusJwtDecoder |
| Applicability | 9 | 15% | 1.35 | Already in build.gradle.kts (`spring-boot-starter-oauth2-resource-server`) |
| Activity | 10 | 15% | 1.50 | Spring team, weekly commits, LTS support |
| Documentation | 9 | 15% | 1.35 | Official Spring docs, guides, samples |
| Code quality | 10 | 15% | 1.50 | Spring standard — extensive tests, well-structured |
| Community | 10 | 10% | 1.00 | ~9.2K stars, massive ecosystem |
| Popularity | 10 | 10% | 1.00 | Industry standard for Spring applications |
| **Tổng điểm** | | | **9.30/10** | |

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Full IAM: JWT, OIDC, SAML, token introspection, key rotation, RBAC |
| Applicability | 4 | 15% | 0.60 | Full platform — heavy for a library; project already has custom auth |
| Activity | 10 | 15% | 1.50 | Red Hat backed, daily commits |
| Documentation | 9 | 15% | 1.35 | Extensive docs, Quarkus/WildFly guides |
| Code quality | 9 | 15% | 1.35 | Enterprise-grade, extensive testing |
| Community | 10 | 10% | 1.00 | ~24K stars, massive community |
| Popularity | 10 | 10% | 1.00 | Industry leader in open source IAM |
| **Tổng điểm** | | | **7.80/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | jjwt | 9 | 10 | 8 | 8 | 9 | 9 | 9 | **8.85** |
| 2 | nimbus-jose-jwt | 10 | 7 | 9 | 7 | 9 | 7 | 8 | **8.30** |
| 3 | java-jwt (Auth0) | 7 | 6 | 7 | 8 | 7 | 8 | 7 | **7.10** |
| 4 | Spring Security OAuth2 RS | 8 | 9 | 10 | 9 | 10 | 10 | 10 | **9.30** |
| 5 | Keycloak | 10 | 4 | 10 | 9 | 9 | 10 | 10 | **7.80** |

---

## 4. Gap Analysis chi tiết

### jjwt — Gap Analysis

**Overall Score**: 8.85 / 10
**URL**: https://github.com/jwtk/jjwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Signing (RS256) | ✅ | | Fluent builder API, all algorithms | - |
| JWT Validation | ✅ | | Claims validation, expiration, issuer | No built-in JWKS fetcher |
| JWKS Support | | ❌ | - | Must build JWKS fetch/cache manually |
| Token Introspection | | ❌ | - | No RFC 7662 support — build manually |
| Token Blacklist | | ❌ | - | Not a library concern — app-level |
| Key Rotation | ⚠️ | | kid header support | No built-in key management |
| Kotlin Support | ✅ | | Fluent API works well with Kotlin | - |
| Test Coverage | ✅ | | Extensive unit tests | - |
| Modular Architecture | ✅ | | api/impl/jackson split | - |

**Verdict**: Đang dùng — continue, fill gaps at application level
**Recommendation**: Tiếp tục dùng jjwt + build JWKS fetcher, blacklist, introspection at app layer
**Reasoning**: Already integrated, well-tested, fills 80% of needs. Missing features (JWKS fetch, introspection) are application concerns, not library concerns.

### nimbus-jose-jwt — Gap Analysis

**Overall Score**: 8.30 / 10
**URL**: https://connect2id.com/products/nimbus-jose-jwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Signing (RS256) | ✅ | | Full RFC compliance | More verbose API than jjwt |
| JWT Validation | ✅ | | DefaultJWTClaimsVerifier, configurable | - |
| JWKS Support | ✅ | | JWKSource, RemoteJWKSet, caching | Built-in JWKS key resolution |
| Token Introspection | ⚠️ | | TokenIntrospectionRequest (SDK) | Part of larger OAuth 2.0 SDK |
| Token Blacklist | | ❌ | - | Not a library concern |
| Key Rotation | ✅ | | JWKSelector, kid-based selection | - |
| Kotlin Support | ✅ | | Standard Java interop | Less fluent than jjwt |
| Documentation | ⚠️ | | Javadoc is good | Less tutorial-friendly |
| Spring Integration | ✅ | | Used internally by Spring Security | - |

**Verdict**: Tham khảo pattern — especially JWKS fetching and claims verification
**Recommendation**: Tham khảo pattern cho JWKS key resolution; don't migrate from jjwt
**Reasoning**: Excellent JWKS support, but migration cost is not justified since jjwt is already deeply integrated. nimbus patterns for JWKS fetching/caching can inform our custom implementation.

### Spring Security OAuth2 Resource Server — Gap Analysis

**Overall Score**: 9.30 / 10
**URL**: https://github.com/spring-projects/spring-security

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Validation | ✅ | | NimbusJwtDecoder, JwtAuthenticationConverter | Opinionated — may conflict with custom JwtAuthFilter |
| JWKS Auto-fetch | ✅ | | Automatic JWKS endpoint resolution + caching | - |
| Issuer Validation | ✅ | | JwtIssuerValidator | - |
| Audience Validation | ✅ | | JwtClaimValidator | - |
| Token Introspection | ✅ | | OpaqueTokenIntrospector, NimbusOpaqueTokenIntrospector | - |
| Token Blacklist | | ❌ | - | App-level concern |
| Custom Token Types | ⚠️ | | Customizable converters | Requires custom JwtDecoder for multi-type tokens |
| Event Sourcing Integration | | ❌ | - | Not event-sourcing-aware |
| Kotlin Support | ✅ | | Official Kotlin DSL | - |

**Verdict**: Cần fork + customize — leverage NimbusJwtDecoder, add custom validators
**Recommendation**: Tham khảo pattern + partial adoption — use Spring Security's JwtDecoder infrastructure alongside custom JwtAuthFilter
**Reasoning**: Already in dependencies. Can leverage NimbusJwtDecoder for JWKS key management while keeping custom JwtAuthFilter for token type discrimination and blacklist checking.

### Keycloak — Gap Analysis

**Overall Score**: 7.80 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Full IAM | ✅ | | Complete identity solution | Too heavy for library use |
| JWT Validation | ✅ | | Token verifier library available | Requires Keycloak server or adapter |
| JWKS + Key Rotation | ✅ | | Automatic key management | Tied to Keycloak server |
| Token Introspection | ✅ | | Full RFC 7662 | Server-dependent |
| RBAC/PBAC | ✅ | | Authorization services | Duplicates existing RBAC module |
| Event Sourcing | | ❌ | - | Not event-sourcing based |
| Integration Effort | | ❌ | - | Would require architectural rework |
| Lightweight Usage | | ❌ | - | Cannot extract just validation logic easily |

**Verdict**: Tham khảo pattern — learn from its validation pipeline and key rotation strategy
**Recommendation**: Tham khảo pattern only — do NOT adopt or fork
**Reasoning**: Keycloak is a full IAM platform. Adopting it would replace the entire auth-service, which contradicts the custom Event Sourcing architecture. However, its token validation pipeline (signature → issuer → exp → audience → custom) and key rotation strategy are excellent reference patterns.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Security OAuth2 RS | 9.30/10 | Tham khảo + partial adoption | JWKS management, JwtDecoder infrastructure |
| 🥈 2 | jjwt | 8.85/10 | Tiếp tục dùng (already integrated) | Core JWT signing/parsing, fluent API |
| 🥉 3 | nimbus-jose-jwt | 8.30/10 | Tham khảo pattern | JWKS fetch/cache pattern, claims verification |
| 4 | Keycloak | 7.80/10 | Tham khảo pattern only | Validation pipeline design, key rotation |
| 5 | java-jwt (Auth0) | 7.10/10 | Bỏ qua | No advantage over jjwt |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Continue with jjwt + augment with custom validation pipeline** | jjwt is already deeply integrated, performant, well-tested. Missing features (JWKS fetch, introspection, blacklist) are application-level concerns best implemented in the service layer. Spring Security's NimbusJwtDecoder can be referenced for JWKS key management patterns. | jjwt already used in JwtService.kt, ServiceTokenService.kt; spring-boot-starter-oauth2-resource-server already in build.gradle.kts |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-11
> **Next step**: Comparison Analysis (comparison_analysis.md)
