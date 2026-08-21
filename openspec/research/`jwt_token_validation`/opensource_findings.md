# Kết quả tìm kiếm Open Source: JWT Token Validation

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày tìm kiếm** | 2026-08-26 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot 3.x / JJWT / Redis / Caffeine |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"JWT validation library Java Kotlin open source GitHub"` | 5 relevant results | Focused on JVM ecosystem |
| 2 | `"JWT token blacklist revocation Redis open source"` | 3 relevant results | Token lifecycle management |
| 3 | `"JWKS key rotation Spring Boot library"` | 2 relevant results | Key management focused |
| 4 | `"Spring Security JWT filter production open source"` | 4 relevant results | Integration patterns |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | JJWT (Java JWT) | https://github.com/jwtk/jjwt | 10.4K+ | Active (2024) | Apache 2.0 | ✅ Có |
| 2 | Nimbus JOSE+JWT | https://bitbucket.org/connect2id/nimbus-jose-jwt | 2K+ (Maven DL) | Active (2024) | Apache 2.0 | ✅ Có |
| 3 | Auth0 java-jwt | https://github.com/auth0/java-jwt | 5.5K+ | Active (2024) | MIT | ✅ Có |
| 4 | Spring Security OAuth2 Resource Server | https://github.com/spring-projects/spring-security | 9.2K+ | Active (2024) | Apache 2.0 | ✅ Có |
| 5 | FusionAuth JWT | https://github.com/FusionAuth/fusionauth-jwt | 600+ | Active (2024) | Apache 2.0 | ❌ Không (smaller ecosystem, less Spring integration) |
| 6 | Vert.x JWT Auth | https://github.com/vert-x3/vertx-auth | 1K+ | Active | Apache 2.0 | ❌ Không (Vert.x-specific, different framework) |
| 7 | Keycloak | https://github.com/keycloak/keycloak | 23K+ | Active | Apache 2.0 | ❌ Không (full IdP, not a library — overkill for validation only) |
| 8 | jose4j | https://bitbucket.org/b_c/jose4j | 500+ | Active | Apache 2.0 | ❌ Không (similar to Nimbus, lower adoption) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured (RS256, claim validation, JWKS) |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack (Kotlin/Spring), easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits, active maintainers |
| **Documentation** | 15% | No docs | README only | Full docs + examples + migration guides |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean, CI/CD |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars, active issues/PRs |
| **Popularity** | 10% | Few users | Growing | Widely adopted in production |

### Kết quả đánh giá

#### JJWT (Java JWT)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full JOSE/JWS/JWE/JWK support, RS256/PS256/EdDSA, claim validation, key parsing |
| Applicability | 10 | 15% | 1.50 | Already used in auth-service — zero migration cost |
| Activity | 8 | 15% | 1.20 | Regular releases, responsive maintainer (Les Hazlewood) |
| Documentation | 8 | 15% | 1.20 | Comprehensive README + wiki + javadoc |
| Code quality | 9 | 15% | 1.35 | Extensive test suite, clean builder pattern API |
| Community | 9 | 10% | 0.90 | 10.4K stars, 1.7K forks, active community |
| Popularity | 9 | 10% | 0.90 | Most popular Java JWT library, Spring Boot default |
| **Tổng điểm** | | | **8.85/10** | |

#### Nimbus JOSE+JWT

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Most complete JOSE implementation — JWS, JWE, JWK, JWA, JWT. JWKS endpoint client, key rotation |
| Applicability | 7 | 15% | 1.05 | Used by Spring Security OAuth2 Resource Server internally; would require migration from JJWT |
| Activity | 9 | 15% | 1.35 | Very active, Connect2id team maintains professionally |
| Documentation | 7 | 15% | 1.05 | Good docs but more complex API surface |
| Code quality | 9 | 15% | 1.35 | Enterprise-grade, extensive tests |
| Community | 7 | 10% | 0.70 | Lower GitHub visibility (Bitbucket), but high Maven downloads |
| Popularity | 8 | 10% | 0.80 | De-facto standard for Spring Security OAuth2 |
| **Tổng điểm** | | | **8.30/10** | |

#### Auth0 java-jwt

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | JWT creation/verification, claim validation. No JWE, limited JWK support |
| Applicability | 6 | 15% | 0.90 | Different API from JJWT, would need migration. Less Kotlin-idiomatic |
| Activity | 8 | 15% | 1.20 | Auth0 team maintenance, regular updates |
| Documentation | 8 | 15% | 1.20 | Good docs, Auth0 ecosystem documentation |
| Code quality | 8 | 15% | 1.20 | Well-tested, clean API |
| Community | 8 | 10% | 0.80 | 5.5K stars, backed by Auth0/Okta |
| Popularity | 7 | 10% | 0.70 | Popular but JJWT more common in Spring ecosystem |
| **Tổng điểm** | | | **7.40/10** | |

#### Spring Security OAuth2 Resource Server

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Built-in JWT decoder, JWKS support, claim validators, token introspection client |
| Applicability | 8 | 15% | 1.20 | Already in build.gradle.kts (spring-boot-starter-oauth2-resource-server). Can complement existing JwtAuthFilter |
| Activity | 10 | 15% | 1.50 | Spring team — weekly commits, LTS support |
| Documentation | 9 | 15% | 1.35 | Excellent Spring docs + guides |
| Code quality | 10 | 15% | 1.50 | Enterprise-grade, Spring quality standards |
| Community | 10 | 10% | 1.00 | Part of Spring Security — massive community |
| Popularity | 10 | 10% | 1.00 | Industry standard for Spring apps |
| **Tổng điểm** | | | **9.35/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | JJWT | 9 | 10 | 8 | 8 | 9 | 9 | 9 | **8.85** |
| 2 | Nimbus JOSE+JWT | 10 | 7 | 9 | 7 | 9 | 7 | 8 | **8.30** |
| 3 | Auth0 java-jwt | 7 | 6 | 8 | 8 | 8 | 8 | 7 | **7.40** |
| 4 | Spring Security OAuth2 RS | 9 | 8 | 10 | 9 | 10 | 10 | 10 | **9.35** |

---

## 4. Gap Analysis chi tiết

### JJWT — Gap Analysis

**Overall Score**: 8.85 / 10
**URL**: https://github.com/jwtk/jjwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Parsing & Validation | ✅ | | Builder pattern, fluent API | - |
| RS256/PS256/EdDSA Signing | ✅ | | All standard algorithms supported | - |
| Claim Validation (exp, nbf, iss) | ✅ | | Automatic exp/nbf validation | Custom claim validators need manual code |
| JWK / JWKS Parsing | ✅ | | JWK key parsing since 0.12.x | No built-in JWKS endpoint client (HTTP fetch) |
| Key Rotation Support | ⚠️ | | kid header support | No automatic key rotation — manual implementation needed |
| Token Blacklist | | ❌ | - | Not a concern of JWT library — application-level |
| Token Introspection (RFC 7662) | | ❌ | - | Not in scope — need custom implementation |
| Two-Tier Cache Integration | | ❌ | - | Application-level concern |
| Kotlin Extensions | ⚠️ | | Java library, Kotlin-compatible | No Kotlin-specific DSL |

**Verdict**: Dùng trực tiếp — already integrated, excellent fit
**Recommendation**: Dùng trực tiếp
**Reasoning**: JJWT is already the JWT library in auth-service. It covers all core JWT operations. Gaps (blacklist, introspection, caching) are application-level concerns, not library responsibilities.

### Nimbus JOSE+JWT — Gap Analysis

**Overall Score**: 8.30 / 10
**URL**: https://bitbucket.org/connect2id/nimbus-jose-jwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Parsing & Validation | ✅ | | Most complete JOSE implementation | More verbose API than JJWT |
| RS256/PS256/EdDSA Signing | ✅ | | All algorithms including JWE encryption | - |
| Claim Validation | ✅ | | JWTClaimsSetVerifier interface | - |
| JWK / JWKS | ✅ | | Built-in JWKSet, RemoteJWKSet with caching | Excellent JWKS endpoint client |
| Key Rotation Support | ✅ | | kid-based key selection, RemoteJWKSet auto-refresh | - |
| Token Blacklist | | ❌ | - | Application-level |
| Token Introspection | ⚠️ | | TokenIntrospectionResponse class available | Client-side only |
| Integration (Spring Boot) | ⚠️ | | Used internally by Spring Security | Would require JJWT migration |

**Verdict**: Tham khảo pattern — excellent JWKS/key rotation patterns
**Recommendation**: Tham khảo pattern
**Reasoning**: Nimbus has superior JWKS client and key rotation support. However, migrating from JJWT would be disruptive. Best to reference Nimbus patterns for JWKS caching and implement similar logic on top of JJWT.

### Auth0 java-jwt — Gap Analysis

**Overall Score**: 7.40 / 10
**URL**: https://github.com/auth0/java-jwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Parsing & Validation | ✅ | | Clean API, Auth0 ecosystem | Less feature-rich than JJWT/Nimbus |
| RS256 Signing | ✅ | | Standard RSA support | - |
| Claim Validation | ✅ | | Built-in claim verifiers | - |
| JWK / JWKS | | ❌ | - | Requires separate jwks-rsa library |
| Key Rotation | ⚠️ | | Via jwks-rsa companion library | Not built-in |
| Kotlin Support | ⚠️ | | Java library | Less Kotlin-idiomatic than JJWT |

**Verdict**: Bỏ qua — JJWT is superior for current stack
**Recommendation**: Bỏ qua
**Reasoning**: No significant advantage over JJWT which is already integrated. Migration cost without clear benefit.

### Spring Security OAuth2 Resource Server — Gap Analysis

**Overall Score**: 9.35 / 10
**URL**: https://github.com/spring-projects/spring-security

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT Validation | ✅ | | NimbusJwtDecoder with full claim validation | Opinionated — may conflict with custom JwtAuthFilter |
| JWKS Integration | ✅ | | JwtDecoderByJwkKeySetUri — auto JWKS fetch + cache | Excellent built-in caching |
| Key Rotation | ✅ | | Automatic kid-based key selection via JWKS | - |
| Claim Validators | ✅ | | JwtTimestampValidator, JwtIssuerValidator, customizable | - |
| Token Introspection | ✅ | | OpaqueTokenIntrospector, NimbusOpaqueTokenIntrospector | Full RFC 7662 client support |
| Token Blacklist | | ❌ | - | Application-level concern |
| Custom Filter Coexistence | ⚠️ | | Can work alongside custom filter | Potential conflict with existing JwtAuthFilter |
| Event Sourcing Integration | | ❌ | - | Not aware of domain events |

**Verdict**: Tham khảo pattern — excellent validation infrastructure
**Recommendation**: Tham khảo pattern (selective adoption)
**Reasoning**: Already in dependencies (`spring-boot-starter-oauth2-resource-server`). Can selectively adopt `JwtDecoder`, `JwtTimestampValidator`, and JWKS caching patterns. Full adoption would require refactoring JwtAuthFilter to use Spring Security's BearerTokenAuthenticationFilter, which is a larger change.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Security OAuth2 RS | 9.35/10 | Tham khảo pattern (selective) | JWKS caching, claim validators, introspection |
| 🥈 2 | JJWT | 8.85/10 | Dùng trực tiếp (đã tích hợp) | Core JWT parsing, signing, verification |
| 🥉 3 | Nimbus JOSE+JWT | 8.30/10 | Tham khảo pattern | JWKS client, RemoteJWKSet caching pattern |
| 4 | Auth0 java-jwt | 7.40/10 | Bỏ qua | N/A — no advantage over JJWT |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| Keep JJWT + selectively adopt Spring Security OAuth2 RS patterns | JJWT already integrated, excellent fit. Spring Security OAuth2 RS provides JWKS caching and claim validator patterns to reference. Custom implementation needed for blacklist caching and token introspection. | Project uses JJWT since inception; migration cost unjustified. Spring OAuth2 RS already in build.gradle.kts. |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-26
> **Next step**: Comparison Analysis (comparison_analysis.md)
